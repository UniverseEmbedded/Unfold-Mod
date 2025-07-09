#!/usr/bin/env python3
"""
save_files_to_temp.py
增强版文件保存脚本，支持：
1. 从 ./files/ 目录选择一个或多个txt文件作为路径列表（支持多选）
2. txt文件中使用!开头的行作为排除规则（类似gitignore）
3. 文件夹递归读取和gitignore式排除功能
4. .env配置项目根目录和是否读取gitignore
5. 输出每个文件的字符数量
"""
import fnmatch
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import List, Tuple

from dotenv import load_dotenv

SEPARATOR = "=" * 80


class FileProcessor:
    def __init__(self):
        # 加载.env文件
        load_dotenv()

        # 获取项目根目录，默认为当前目录
        self.project_root = Path(os.getenv('PROJECT_ROOT', '.')).resolve()

        # 是否读取gitignore文件
        self.use_gitignore = os.getenv('USE_GITIGNORE', 'false').lower() in ('true', '1', 'yes')

        # 默认排除模式
        self.default_exclude_patterns = [
            '.git/**',
            '.venv/**',
            '__pycache__/**',
            '*.pyc',
            '*.pyo',
            '*.pyd',
            '.DS_Store',
            'Thumbs.db',
            '*.log',
            'temp/**',
            'tmp/**',
            'node_modules/**',
            '.env',
            '*.bak',
            '*.swp',
            '*.tmp'
        ]

    def select_txt_file_from_files_dir(self) -> Tuple[List[str], List[str]]:
        """从 ./files/ 目录选择一个或多个txt文件并读取其中的路径和排除规则"""
        files_dir = Path('./files')

        if not files_dir.exists():
            print("[INFO] ./files/ 目录不存在，将使用手动输入模式", file=sys.stderr)
            return [], []

        # 排序以保证每次运行文件列表顺序一致
        txt_files = sorted(list(files_dir.glob('*.txt')))

        if not txt_files:
            print("[INFO] ./files/ 目录下没有找到txt文件，将使用手动输入模式", file=sys.stderr)
            return [], []

        print("发现以下txt文件：", file=sys.stderr)
        for i, txt_file in enumerate(txt_files, 1):
            print(f"{i:2d}. {txt_file.name}", file=sys.stderr)

        print(" 0. 手动输入路径", file=sys.stderr)

        while True:
            try:
                # 提示用户可以输入多个编号
                choice_str = input("请选择文件编号（可输入多个，用空格分隔；0为手动输入）: ")
                if not choice_str.strip():  # 处理空输入
                    continue

                choices = choice_str.strip().split()

                if '0' in choices:
                    print("[INFO] 已选择手动输入模式。", file=sys.stderr)
                    return [], []

                selected_file_indices = set()
                invalid_choices = []
                for c in choices:
                    try:
                        choice_num = int(c)
                        if 1 <= choice_num <= len(txt_files):
                            selected_file_indices.add(choice_num - 1)
                        else:
                            invalid_choices.append(c)
                    except ValueError:
                        invalid_choices.append(c)

                if invalid_choices:
                    print(f"[ERROR] 输入无效或超出范围: {', '.join(invalid_choices)}", file=sys.stderr)
                    print(f"请输入 1-{len(txt_files)} 之间的数字，用空格分隔。", file=sys.stderr)
                    continue

                if not selected_file_indices:
                    continue

                # 从所有选中的文件中汇总路径和排除规则
                all_paths = []
                all_excludes = []
                selected_file_names = []

                # 按索引顺序处理，保证可预测性
                for index in sorted(list(selected_file_indices)):
                    selected_file = txt_files[index]
                    selected_file_names.append(selected_file.name)
                    paths, excludes = self._parse_paths_and_excludes_from_file(selected_file)
                    all_paths.extend(paths)
                    all_excludes.extend(excludes)

                print(f"[INFO] 已选择并汇总文件: {', '.join(selected_file_names)}", file=sys.stderr)

                # 去重（保留顺序）
                unique_paths = list(dict.fromkeys(all_paths))
                unique_excludes = list(dict.fromkeys(all_excludes))

                print(f"[INFO] 汇总后共 {len(unique_paths)} 个路径，{len(unique_excludes)} 个排除规则", file=sys.stderr)

                return unique_paths, unique_excludes

            except KeyboardInterrupt:
                print("\n已取消选择，将使用手动输入模式", file=sys.stderr)
                return [], []

    def _parse_paths_and_excludes_from_file(self, file_path: Path) -> Tuple[List[str], List[str]]:
        """从文件中读取路径列表和排除规则"""
        try:
            content = file_path.read_text(encoding="utf-8", errors="replace")
            paths = []
            excludes = []

            for line in content.splitlines():
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                elif line.startswith('!'):
                    # 排除规则
                    excludes.append(line[1:])  # 去掉感叹号
                else:
                    # 包含路径
                    paths.append(line)

            print(f"[INFO] 从 {file_path.name} 读取到 {len(paths)} 个路径，{len(excludes)} 个排除规则", file=sys.stderr)
            return paths, excludes
        except Exception as exc:
            print(f"[ERROR] 读取文件 {file_path} 失败: {exc}", file=sys.stderr)
            return [], []

    def read_paths_from_stdin(self) -> List[str]:
        """从标准输入读取路径"""
        print(
            "请粘贴文件/文件夹完整路径（每行一个），"
            "使用!开头表示排除规则，结束后按 Ctrl+Z ↵（Windows）或 Ctrl+D（Linux/macOS）：",
            file=sys.stderr,
        )

        try:
            paths = []
            for line in sys.stdin.read().splitlines():
                line = line.strip()
                if line and not line.startswith('#'):
                    paths.append(line)
            return paths
        except KeyboardInterrupt:
            print("\n操作已取消", file=sys.stderr)
            return []

    def parse_stdin_paths_and_excludes(self, raw_paths: List[str]) -> Tuple[List[str], List[str]]:
        """从stdin输入中分离路径和排除规则"""
        paths = []
        excludes = []

        for line in raw_paths:
            if line.startswith('!'):
                excludes.append(line[1:])  # 去掉感叹号
            else:
                paths.append(line)

        return paths, excludes

    def load_gitignore_patterns(self) -> List[str]:
        """加载gitignore文件中的排除模式"""
        patterns = []

        if not self.use_gitignore:
            return patterns

        # 从项目根目录开始，向上查找.gitignore文件
        current_dir = self.project_root
        while current_dir != current_dir.parent:
            gitignore_path = current_dir / '.gitignore'
            if gitignore_path.exists():
                try:
                    content = gitignore_path.read_text(encoding="utf-8", errors="replace")
                    for line in content.splitlines():
                        line = line.strip()
                        if line and not line.startswith('#') and not line.startswith('!'):
                            patterns.append(line)
                    print(f"[INFO] 从 {gitignore_path} 加载了排除规则", file=sys.stderr)
                except Exception as exc:
                    print(f"[WARNING] 读取 {gitignore_path} 失败: {exc}", file=sys.stderr)
            current_dir = current_dir.parent

        return patterns

    def get_all_exclude_patterns(self, file_excludes: List[str] = None) -> List[str]:
        """获取所有排除模式"""
        patterns = self.default_exclude_patterns.copy()

        # 添加gitignore规则
        if self.use_gitignore:
            patterns.extend(self.load_gitignore_patterns())

        # 添加文件中的排除规则
        if file_excludes:
            patterns.extend(file_excludes)

        return patterns

    def should_exclude(self, path: Path, exclude_patterns: List[str]) -> bool:
        """检查路径是否应该被排除"""
        # 转换为相对于项目根目录的路径
        try:
            rel_path = path.relative_to(self.project_root)
        except ValueError:
            # 如果不在项目根目录下，使用绝对路径
            rel_path = path

        path_str = str(rel_path).replace('\\', '/')

        for pattern in exclude_patterns:
            # 处理目录模式
            if pattern.endswith('/'):
                if fnmatch.fnmatch(path_str + '/', pattern) or fnmatch.fnmatch(path_str, pattern[:-1]):
                    return True
            # 处理递归模式
            elif '**' in pattern:
                if fnmatch.fnmatch(path_str, pattern):
                    return True
                # 检查父目录
                parts = path_str.split('/')
                for i in range(len(parts)):
                    test_path = '/'.join(parts[:i + 1])
                    if fnmatch.fnmatch(test_path, pattern):
                        return True
            # 普通模式匹配
            else:
                if fnmatch.fnmatch(path_str, pattern):
                    return True
                # 检查文件名匹配
                if fnmatch.fnmatch(path.name, pattern):
                    return True

        return False

    def collect_files(self, paths: List[str], exclude_patterns: List[str] = None) -> List[Path]:
        """收集所有需要处理的文件"""
        if exclude_patterns is None:
            exclude_patterns = self.get_all_exclude_patterns()

        all_files = []

        for path_str in paths:
            path = Path(path_str)

            # 处理相对路径
            if not path.is_absolute():
                path = self.project_root / path

            if not path.exists():
                print(f"[WARNING] 路径不存在: {path}", file=sys.stderr)
                continue

            if path.is_file():
                if not self.should_exclude(path, exclude_patterns):
                    all_files.append(path)
                else:
                    print(f"[EXCLUDED] 文件: {path}", file=sys.stderr)
            elif path.is_dir():
                print(f"[INFO] 正在扫描目录: {path}", file=sys.stderr)
                for file_path in path.rglob('*'):
                    if file_path.is_file() and not self.should_exclude(file_path, exclude_patterns):
                        all_files.append(file_path)
                    elif self.should_exclude(file_path, exclude_patterns):
                        # 仅在调试时或需要详细输出时取消注释下一行
                        # print(f"[EXCLUDED] 路径: {file_path}", file=sys.stderr)
                        pass

        return sorted(set(all_files))  # 去重并排序

    def ensure_temp_dir(self, temp_dir: Path) -> None:
        """确保temp目录存在"""
        try:
            temp_dir.mkdir(parents=True, exist_ok=True)
        except Exception as exc:
            print(f"[ERROR] 无法创建目录 {temp_dir}: {exc}", file=sys.stderr)
            sys.exit(1)

    def aggregate_to_file(self, files: List[Path], out_path: Path, original_paths: List[str],
                          exclude_rules: List[str]) -> None:
        """将所有文件内容聚合到输出文件，并包含字符数统计。"""

        # 1. 预处理文件以获取内容和字符数
        file_data = []
        total_chars = 0
        for file_path in files:
            try:
                content = file_path.read_text(encoding="utf-8", errors="replace")
                char_count = len(content)
                file_data.append({'path': file_path, 'content': content, 'chars': char_count})
                total_chars += char_count
            except Exception as exc:
                error_message = f"[ERROR] 读取失败: {exc}"
                print(f"[ERROR] 无法读取 {file_path}: {exc}", file=sys.stderr)
                # 仍然添加到列表中，以便在输出文件中报告错误
                file_data.append({'path': file_path, 'content': error_message, 'chars': 0})

        # 2. 写入输出文件
        with out_path.open("w", encoding="utf-8") as fout:
            # 写入文件头信息
            fout.write(f"{SEPARATOR}\n")
            fout.write("文件聚合报告\n")
            fout.write(f"{SEPARATOR}\n")
            fout.write(f"聚合时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            fout.write(f"项目根目录: {self.project_root}\n")
            fout.write(f"总计处理文件数: {len(file_data)}\n")
            fout.write(f"总计字符数: {total_chars}\n")
            fout.write(f"原始输入路径数: {len(original_paths)}\n")
            if exclude_rules:
                fout.write(f"文件排除规则数: {len(exclude_rules)}\n")
            fout.write("\n")

            # 写入原始输入路径
            fout.write("原始输入路径:\n")
            # 使用dict.fromkeys去重并保留顺序
            for i, path in enumerate(list(dict.fromkeys(original_paths)), 1):
                fout.write(f"{i:3d}. {path}\n")
            fout.write("\n")

            # 写入排除规则
            if exclude_rules:
                fout.write("文件中的排除规则:\n")
                # 使用dict.fromkeys去重并保留顺序
                for i, rule in enumerate(list(dict.fromkeys(exclude_rules)), 1):
                    fout.write(f"{i:3d}. !{rule}\n")
                fout.write("\n")

            # 写入实际处理的文件列表 (并附上字符数)
            fout.write("实际处理的文件列表 (包含字符数):\n")
            for i, data in enumerate(file_data, 1):
                fout.write(f"{i:3d}. {data['path']} ({data['chars']} 个字符)\n")

            fout.write(f"\n{SEPARATOR}\n")
            fout.write("文件内容开始\n")
            fout.write(f"{SEPARATOR}\n\n")

            # 写入各个文件的内容
            for data in file_data:
                path = data['path']
                content = data['content']
                char_count = data['chars']

                # 写入分隔线、路径、字符数、分隔线
                fout.write(f"{SEPARATOR}\n")
                fout.write(f"{path} ({char_count} 个字符)\n")
                fout.write(f"{SEPARATOR}\n")
                fout.write(content)

                # 保证文件内容和下一个分隔符之间有空行
                if not content.endswith("\n"):
                    fout.write("\n")
                fout.write("\n")


def main():
    processor = FileProcessor()

    print(f"[INFO] 项目根目录: {processor.project_root}", file=sys.stderr)
    if processor.use_gitignore:
        print("[INFO] 已启用gitignore支持", file=sys.stderr)

    # 首先尝试从files目录选择
    paths, file_excludes = processor.select_txt_file_from_files_dir()

    # 如果没有选择文件，则手动输入
    if not paths and not file_excludes: # 确保两个都为空才进入手动模式
        raw_paths = processor.read_paths_from_stdin()
        if raw_paths:
            paths, file_excludes = processor.parse_stdin_paths_and_excludes(raw_paths)

    if not paths:
        print("未检测到任何路径，脚本退出。", file=sys.stderr)
        return

    # 获取所有排除规则
    exclude_patterns = processor.get_all_exclude_patterns(file_excludes)

    # 收集所有文件
    files = processor.collect_files(paths, exclude_patterns)

    if not files:
        print("根据输入路径和排除规则，没有找到任何需要处理的文件。", file=sys.stderr)
        return

    print(f"[INFO] 共找到 {len(files)} 个文件需要处理", file=sys.stderr)

    # 确保temp目录存在
    temp_dir = Path("./temp")
    processor.ensure_temp_dir(temp_dir)

    # 生成输出文件名
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")
    out_file = temp_dir / f"{timestamp}.txt"

    # 聚合文件内容
    processor.aggregate_to_file(files, out_file, paths, file_excludes)

    print(f"[OK] 已将 {len(files)} 个文件的内容保存到 {out_file}", file=sys.stderr)
    # print(f"[OK] 已将 {len(files)} 个文件的内容保存到 {os.path.abspath(out_file)}", file=sys.stderr)


if __name__ == "__main__":
    main()