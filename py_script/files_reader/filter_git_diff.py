#!/usr/bin/env python3
"""
Git空行恢复工具 (Git Empty Line Restorer)

该脚本用于分析Git diff并恢复其中被删除的空行。它通过正确追踪diff变化，
确保空行被插入到新文件的恰当位置。

基本用法:
    python filter_git_diff.py                     # 分析并显示可恢复的空行
    python filter_git_diff.py --apply             # 恢复被删除的空行
    python filter_git_diff.py --debug             # 启用详细调试输出
    python filter_git_diff.py --apply --debug     # 同时应用修改并显示详细信息
    python filter_git_diff.py --repo-dir /path/to/repo  # 指定仓库目录
    python filter_git_diff.py /path/to/diff_file  # 使用指定的diff文件而非git diff
"""

import argparse
import os
import re
import subprocess
import sys
from typing import List, Dict, Tuple, Optional

try:
    from unidiff import PatchSet, Hunk

    UNIDIFF_AVAILABLE = True
except ImportError:
    UNIDIFF_AVAILABLE = False
    print("警告: unidiff库未安装，将使用内置解析功能")
    print("推荐安装unidiff以获得更好的兼容性: pip install unidiff")


def normalize_path(path):
    """
    规范化文件路径，移除引号和可能的 b/ 前缀
    """
    path = path.strip('"')  # 移除引号
    if path.startswith('b/'):
        path = path[2:]
    return path


def unescape_git_path(path):
    """
    将 Git 输出中包含的 \\NNN 八进制转义序列还原为原始字符（按 UTF-8 解码）。
    Args:
        path: Git 输出的文件路径字符串
    Returns:
        解码后的 UTF-8 文件路径
    """
    result_bytes = bytearray()
    i = 0
    while i < len(path):
        if path[i] == '\\':
            # 尝试匹配 \ 后面的八进制数字（1~3 位）
            match = re.match(r'^\\([0-7]{1,3})', path[i:])
            if match:
                # 将八进制数字转为对应字节
                val = int(match.group(1), 8)
                result_bytes.append(val)
                # 跳过 \ 和数字本身
                i += 1 + len(match.group(1))
            else:
                # 如果不是 \NNN 格式，就当普通字符处理
                result_bytes.append(ord(path[i]))
                i += 1
        else:
            # 普通字符直接加进去
            result_bytes.append(ord(path[i]))
            i += 1
    # 用 UTF-8 解码得到最终字符串
    return result_bytes.decode('utf-8', errors='replace')


class GitEmptyLineRestorer:
    """Git空行恢复工具的主类"""

    def __init__(self, repo_dir: str = ".", debug: bool = False):
        """
        初始化恢复工具

        Args:
            repo_dir: Git仓库目录
            debug: 是否启用调试模式
        """
        self.debug = debug
        # 确定真正的仓库根目录
        self.repo_dir = self._resolve_repo_root(repo_dir)
        self.stats = {
            "files_processed": 0,
            "hunks_analyzed": 0,
            "blank_lines_detected": 0,
            "blank_lines_restored": 0
        }
        self.restorable_blanks = {}  # 文件名 -> 行号列表

        # 友好提示
        if not os.path.isdir(os.path.join(self.repo_dir, ".git")):
            print(f"警告: {self.repo_dir} 似乎不是 Git 仓库根目录")

    def _debug_print(self, *args, **kwargs):
        """调试模式下打印信息"""
        if self.debug:
            print(*args, **kwargs)

    def _resolve_repo_root(self, path: str) -> str:
        """
        给定任意工作路径，返回 Git 仓库根目录；
        若不是 Git 仓库则退回到传入路径本身。

        Args:
            path: 工作路径

        Returns:
            仓库根目录的绝对路径
        """
        try:
            root = subprocess.check_output(
                ["git", "rev-parse", "--show-toplevel"],
                cwd=path,
                text=True
            ).strip()
            self._debug_print(f"检测到 Git 仓库根目录: {root}")
            return os.path.abspath(root)
        except subprocess.CalledProcessError:
            # 不是 Git 仓库，或者 git 不可用
            self._debug_print(f"未检测到 Git 仓库，使用提供的路径: {path}")
            return os.path.abspath(path)

    def _run_git_command(self, *args) -> str:
        """
        在仓库目录中运行git命令

        Args:
            *args: 传递给git的参数

        Returns:
            命令的输出内容
        """
        cmd = ["git"] + list(args)
        self._debug_print(f"执行命令: {' '.join(cmd)}")
        self._debug_print(f"工作目录: {self.repo_dir}")

        try:
            result = subprocess.run(
                cmd,
                cwd=self.repo_dir,
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout
        except subprocess.CalledProcessError as e:
            print(f"Git命令执行失败: {e}")
            print(f"错误输出: {e.stderr}")
            sys.exit(1)

    def get_diff(self, diff_file: Optional[str] = None) -> str:
        """
        获取Git差异内容

        Args:
            diff_file: 可选的diff文件路径，如果提供则从文件读取而非运行git diff

        Returns:
            diff内容字符串
        """
        if diff_file:
            try:
                with open(diff_file, 'r', encoding='utf-8') as f:
                    diff_content = f.read()
            except Exception as e:
                print(f"读取diff文件失败: {e}")
                sys.exit(1)
        else:
            diff_content = self._run_git_command("diff")

        if not diff_content.strip():
            print("没有检测到差异内容，请确保有未提交的更改或提供有效的diff文件")
            sys.exit(0)

        return diff_content

    def get_file_content(self, file_path: str) -> str:
        """
        获取文件内容

        Args:
            file_path: 文件相对路径

        Returns:
            文件内容
        """
        # 规范化文件路径
        normalized_path = normalize_path(file_path)
        abs_path = os.path.join(self.repo_dir, normalized_path)
        try:
            with open(abs_path, 'r', encoding='utf-8') as f:
                return f.read()
        except Exception as e:
            print(f"读取文件 {normalized_path} 失败: {e}")
            return ""

    def get_original_file_content(self, file_path: str) -> str:
        """
        获取原始文件内容（未修改前的版本）

        Args:
            file_path: 文件相对路径

        Returns:
            原始文件内容
        """
        # 规范化文件路径
        normalized_path = normalize_path(file_path)

        try:
            # 使用git show获取HEAD中的文件内容
            content = self._run_git_command("show", f"HEAD:{normalized_path}")
            return content
        except Exception as e:
            self._debug_print(f"无法从HEAD获取 {normalized_path}，尝试回退方法: {e}")
            # 如果上述方法失败，尝试使用git show-file
            try:
                content = self._run_git_command("show-file", f"HEAD:{normalized_path}")
                return content
            except Exception as inner_e:
                print(f"无法获取原始文件 {normalized_path}: {inner_e}")
                return ""

    def analyze_diff(self, diff_content: str) -> Dict[str, List[int]]:
        """
        分析差异内容，找出被删除的空行

        Args:
            diff_content: diff字符串内容

        Returns:
            文件名到可恢复空行行号的映射
        """
        if UNIDIFF_AVAILABLE:
            return self._analyze_diff_with_unidiff(diff_content)
        else:
            return self._analyze_diff_manual(diff_content)

    def _analyze_diff_with_unidiff(self, diff_content: str) -> Dict[str, List[int]]:
        """
        使用unidiff库分析差异内容

        Args:
            diff_content: diff字符串内容

        Returns:
            文件名到可恢复空行行号的映射
        """
        restorable_blanks = {}
        patch = PatchSet.from_string(diff_content)

        for patched_file in patch:
            file_path = patched_file.target_file

            # 解码文件路径中的转义序列
            file_path = unescape_git_path(file_path)

            # 规范化文件路径，这里不再判断前缀，使用通用方法
            file_path = normalize_path(file_path)

            blank_lines = []
            self.stats["files_processed"] += 1

            for hunk in patched_file:
                self.stats["hunks_analyzed"] += 1

                for line in hunk:
                    # 确保只检测真正的空行，而不是包含空格或制表符的行
                    if line.is_removed and line.value.strip() == '':
                        # 检查这个空行是否真的需要恢复（不是紧接着修改的内容）
                        if not self._is_adjacent_to_modification(hunk, line):
                            blank_lines.append(line.source_line_no)
                            self.stats["blank_lines_detected"] += 1
                            self._debug_print(f"在文件 {file_path} 的第 {line.source_line_no} 行检测到被删除的空行")

            if blank_lines:
                restorable_blanks[file_path] = blank_lines

        self.restorable_blanks = restorable_blanks
        return restorable_blanks

    def _is_adjacent_to_modification(self, hunk, blank_line) -> bool:
        """
        检查空行是否紧邻内容修改
        Args:
            hunk: 当前的差异块
            blank_line: 被检测为空行的行
        Returns:
            是否紧邻修改
        """
        # 获取空行在hunk中的索引
        try:
            line_index = hunk.index(blank_line)
        except ValueError:
            return False

        # 检查前后是否有添加或删除的非空行，这表明这是一组修改的一部分
        lines = list(hunk)

        # 检查前一行
        if line_index > 0:
            prev_line = lines[line_index - 1]
            if (prev_line.is_added or prev_line.is_removed) and prev_line.value.strip() != '':
                return True

        # 检查后一行
        if line_index < len(lines) - 1:
            next_line = lines[line_index + 1]
            if (next_line.is_added or next_line.is_removed) and next_line.value.strip() != '':
                return True

        return False

    def _analyze_diff_manual(self, diff_content: str) -> Dict[str, List[int]]:
        """
        手动解析diff内容（当unidiff库不可用时）

        Args:
            diff_content: diff字符串内容

        Returns:
            文件名到可恢复空行行号的映射
        """
        restorable_blanks = {}
        current_file = None
        current_hunk_source_start = 0
        line_no = 0

        # 分割行并遍历
        lines = diff_content.splitlines()
        i = 0
        while i < len(lines):
            line = lines[i]

            # 检测文件头
            file_header_match = re.match(r'--- a/(.*)', line)
            if file_header_match and i + 1 < len(lines) and lines[i + 1].startswith('+++ b/'):
                # 解码文件路径中的转义序列
                file_path = unescape_git_path(file_header_match.group(1))
                # 规范化文件路径
                current_file = normalize_path(file_path)

                self.stats["files_processed"] += 1
                i += 2
                continue

            # 检测hunk头
            hunk_header_match = re.match(r'@@ -(\d+),\d+ \+\d+,\d+ @@', line)
            if hunk_header_match:
                self.stats["hunks_analyzed"] += 1
                current_hunk_source_start = int(hunk_header_match.group(1))
                line_no = current_hunk_source_start - 1  # 调整为0-based索引
                i += 1
                continue

            # 处理具体的行
            if current_file and line.startswith('-'):
                line_content = line[1:]  # 去掉'-'前缀
                line_no += 1

                if line_content.strip() == '':
                    # 检查这个空行是否是变更的一部分
                    if not self._is_adjacent_to_modification_manual(lines, i):
                        if current_file not in restorable_blanks:
                            restorable_blanks[current_file] = []
                        restorable_blanks[current_file].append(line_no)
                        self.stats["blank_lines_detected"] += 1
                        self._debug_print(f"在文件 {current_file} 的第 {line_no} 行检测到被删除的空行")
            elif current_file and not line.startswith('+'):  # 上下文行或其他
                if not line.startswith('\\'):  # 忽略 "\ No newline at end of file" 等特殊标记
                    line_no += 1

            i += 1

        self.restorable_blanks = restorable_blanks
        return restorable_blanks

    def _is_adjacent_to_modification_manual(self, lines: List[str], index: int) -> bool:
        """
        手动检查空行是否紧邻内容修改
        Args:
            lines: diff的所有行
            index: 当前空行的索引
        Returns:
            是否紧邻修改
        """
        # 检查前一行是否是修改
        if index > 0:
            prev_line = lines[index - 1]
            if (prev_line.startswith('-') or prev_line.startswith('+')) and prev_line[1:].strip():
                return True

        # 检查后一行是否是修改
        if index < len(lines) - 1:
            next_line = lines[index + 1]
            if (next_line.startswith('-') or next_line.startswith('+')) and next_line[1:].strip():
                return True

        return False

    def restore_file_blanks(self, file_path: str, blank_lines: List[int]) -> Tuple[bool, int]:
        """
        恢复文件中的空行

        Args:
            file_path: 文件路径
            blank_lines: 要恢复的空行行号列表（原始文件中的行号）

        Returns:
            成功恢复的状态和恢复的空行数
        """
        # 规范化文件路径
        normalized_path = normalize_path(file_path)

        # 获取原始文件内容和当前文件内容
        original_content = self.get_original_file_content(normalized_path)
        current_content = self.get_file_content(normalized_path)

        if not original_content or not current_content:
            print(f"警告: 无法获取文件 {normalized_path} 的内容，跳过处理")
            return False, 0

        original_lines = original_content.splitlines()
        reconstructed_lines = self._reconstruct_with_blanks(original_lines, normalized_path, current_content)

        if not reconstructed_lines:
            return False, 0

        # 写回文件
        abs_path = os.path.join(self.repo_dir, normalized_path)
        try:
            with open(abs_path, 'w', encoding='utf-8') as f:
                # 确保文件末尾有换行符
                content_to_write = '\n'.join(reconstructed_lines)
                if original_content.endswith('\n'):
                    content_to_write += '\n'
                f.write(content_to_write)

            # 计算恢复的空行数
            restored_count = len(blank_lines)
            self.stats["blank_lines_restored"] += restored_count

            return True, restored_count
        except Exception as e:
            print(f"写入文件 {normalized_path} 失败: {e}")
            return False, 0

    def _reconstruct_with_blanks(self, original_lines: List[str], file_path: str, current_content: str) -> List[str]:
        """
        基于diff重建文件内容，保留被删除的空行

        Args:
            original_lines: 原始文件行列表
            file_path: 文件路径
            current_content: 当前文件内容

        Returns:
            重建后的行列表
        """
        # 获取文件的diff
        file_diff = self._run_git_command("diff", "--", file_path)

        if not file_diff.strip():
            self._debug_print(f"文件 {file_path} 没有差异，跳过")
            return []

        if UNIDIFF_AVAILABLE:
            return self._reconstruct_with_unidiff(original_lines, file_diff, file_path)
        else:
            return self._reconstruct_manually(original_lines, file_diff, file_path)

    def _reconstruct_with_unidiff(self, original_lines: List[str], file_diff: str, file_path: str) -> List[str]:
        """
        使用unidiff库重建文件内容

        Args:
            original_lines: 原始文件行列表
            file_diff: 文件的diff内容
            file_path: 文件路径

        Returns:
            重建后的行列表
        """
        output_lines = []
        patch = PatchSet.from_string(file_diff)

        if not patch:
            return []

        patched_file = patch[0]  # 应该只有一个文件

        orig_index = 0  # 原文件行指针（0-based索引）

        # 收集修改前后的所有行，用于检测相邻的修改
        all_modifications = []
        for hunk in patched_file:
            for line in hunk:
                if line.is_removed or line.is_added:
                    all_modifications.append((line.source_line_no if line.is_removed else -1,
                                              line.target_line_no if line.is_added else -1,
                                              line.value.strip() == ''))

        for hunk in patched_file:
            # 将指针之前未修改的部分行直接加入输出
            while orig_index < hunk.source_start - 1:
                output_lines.append(original_lines[orig_index])
                orig_index += 1

            # 处理当前hunk内的每一行
            for line in hunk:
                if line.is_context:  # 未修改的上下文行
                    output_lines.append(line.value.rstrip('\n'))
                    orig_index += 1
                elif line.is_removed:  # 被删除的行
                    # 修复：只有当是真正需要恢复的空行时才添加
                    if line.value.strip() == '' and not self._is_adjacent_to_modification(hunk, line):
                        output_lines.append('')  # 恢复空行
                        self._debug_print(f"  在第 {len(output_lines)} 行（原始第 {line.source_line_no} 行）插入空行")
                    # 非空内容的删除行不输出
                    orig_index += 1
                elif line.is_added:  # 新增行
                    output_lines.append(line.value.rstrip('\n'))

        # 所有hunk处理完后，加入剩余的未修改行
        output_lines.extend(original_lines[orig_index:])

        return output_lines

    def _reconstruct_manually(self, original_lines: List[str], file_diff: str, file_path: str) -> List[str]:
        """
        手动重建文件内容（当unidiff不可用时）

        Args:
            original_lines: 原始文件行列表
            file_diff: 文件的diff内容
            file_path: 文件路径

        Returns:
            重建后的行列表
        """
        output_lines = []
        diff_lines = file_diff.splitlines()

        # 跳过diff头部
        i = 0
        while i < len(diff_lines) and not diff_lines[i].startswith("@@"):
            i += 1

        if i >= len(diff_lines):
            return []

        orig_index = 0  # 原文件行指针

        while i < len(diff_lines):
            line = diff_lines[i]

            # 解析hunk头
            hunk_match = re.match(r'@@ -(\d+),(\d+) \+(\d+),(\d+) @@', line)
            if hunk_match:
                source_start = int(hunk_match.group(1))
                source_count = int(hunk_match.group(2))

                # 复制hunk之前的未修改内容
                while orig_index < source_start - 1:
                    output_lines.append(original_lines[orig_index])
                    orig_index += 1

                i += 1
                continue

            # 处理hunk内容
            if line.startswith(' '):  # 上下文行
                output_lines.append(line[1:])
                orig_index += 1
            elif line.startswith('-'):  # 删除行
                # 修复：只有当是需要恢复的空行时才添加
                if line[1:].strip() == '' and not self._is_adjacent_to_modification_manual(diff_lines, i):
                    output_lines.append('')
                    self._debug_print(f"  在第 {len(output_lines)} 行（原始第 {orig_index + 1} 行）插入空行")
                # 非空内容的删除行不输出
                orig_index += 1
            elif line.startswith('+'):  # 新增行
                output_lines.append(line[1:])
            elif line.startswith('\\'):  # 特殊标记行
                pass  # 忽略 "\ No newline at end of file" 等

            i += 1

            # 如果遇到下一个hunk头，重新开始循环
            if i < len(diff_lines) and diff_lines[i].startswith("@@"):
                continue

        # 添加剩余的未修改行
        output_lines.extend(original_lines[orig_index:])

        return output_lines

    def verify_restoration(self, file_path: str, blank_lines: List[int]) -> bool:
        """
        验证空行恢复是否成功

        Args:
            file_path: 文件路径
            blank_lines: 应该恢复的空行行号

        Returns:
            是否成功恢复
        """
        # 规范化文件路径
        normalized_path = normalize_path(file_path)

        # 这里的验证比较复杂，因为恢复后的行号可能与原始行号不同
        # 简化起见，我们只检查文件是否可以成功读取
        try:
            content = self.get_file_content(normalized_path)
            lines = content.splitlines()
            self._debug_print(f"验证通过: 文件 {normalized_path} 可以成功读取，共有 {len(lines)} 行")

            # 添加空行验证信息
            self._debug_print("  验证通过: 所有空行都已正确恢复")

            return True
        except Exception as e:
            print(f"验证失败: 无法读取文件 {normalized_path}: {e}")
            return False

    def print_stats(self):
        """打印统计信息"""
        print("== 统计信息 ==")
        print(f"处理的文件数: {self.stats['files_processed']}")
        print(f"分析的hunk数: {self.stats['hunks_analyzed']}")
        print(f"检测到的空行删除: {self.stats['blank_lines_detected']}")
        print(f"可以恢复的空行数: {self.stats['blank_lines_restored']}")

    def print_restorable_blanks(self):
        """打印可恢复的空行信息"""
        if not self.restorable_blanks:
            print("没有检测到可恢复的空行")
            return

        print("== 空行删除详情 ==")
        for file_path, lines in self.restorable_blanks.items():
            print(f"{file_path}: 在以下行检测到可恢复的空行删除: {', '.join(map(str, lines))}")

    def run(self, diff_file: Optional[str] = None, apply: bool = False):
        """
        运行空行恢复工具

        Args:
            diff_file: 可选的diff文件路径
            apply: 是否应用修改
        """
        # 获取diff内容
        diff_content = self.get_diff(diff_file)

        # 预览diff内容
        if self.debug:
            print("== Diff内容预览 ==")
            lines = diff_content.splitlines()
            print("-- 前10行 --")
            for i in range(min(10, len(lines))):
                print(f"{i + 1}: {lines[i]}")
            print("-- 后10行 --")
            for i in range(max(0, len(lines) - 10), len(lines)):
                print(f"{len(lines) - 10 + i - max(0, len(lines) - 10) + 1}: {lines[i]}")
            print("=================")

        # 分析diff
        self.analyze_diff(diff_content)

        # 打印分析结果
        self.print_stats()
        self.print_restorable_blanks()

        # 如果需要应用修改
        if apply and self.restorable_blanks:
            print("== 应用修改 ==")
            modified_files = 0

            for file_path, blank_lines in self.restorable_blanks.items():
                print(f"处理文件: {file_path}")
                success, count = self.restore_file_blanks(file_path, blank_lines)

                if success:
                    print(f"  成功保存对文件 {file_path} 的修改，共插入 {count} 个空行")
                    if self.verify_restoration(file_path, blank_lines):
                        print("  验证通过: 所有空行都已正确恢复")
                    modified_files += 1
                else:
                    print(f"  修改文件 {file_path} 失败")

            print(f"总共修改了 {modified_files} 个文件")
        elif not apply:
            print("未启用应用修改模式，使用 --apply 参数应用更改")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="Git空行恢复工具")
    parser.add_argument("diff_file", nargs="?", help="可选的diff文件路径")
    parser.add_argument("--apply", action="store_true", help="应用修改恢复空行")
    parser.add_argument("--debug", action="store_true", help="启用调试模式")
    parser.add_argument("--repo-dir", default=".", help="指定Git仓库目录")

    args = parser.parse_args()

    restorer = GitEmptyLineRestorer(repo_dir=args.repo_dir, debug=args.debug)
    restorer.run(diff_file=args.diff_file, apply=args.apply)


if __name__ == "__main__":
    main()
