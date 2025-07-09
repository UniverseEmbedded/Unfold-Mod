#!/usr/bin/env python3
"""
scan_and_save_paths.py
一个用于扫描指定目录并保存所有路径（文件和目录）的脚本。

功能特性:
1. 从 ./files/ 目录选择一个或多个txt文件作为扫描目标列表（支持多选）。
2. txt文件中使用!开头的行作为排除规则（类似gitignore）。
3. 递归扫描文件夹并支持gitignore式的排除功能。
4. 通过.env文件配置项目根目录和是否读取gitignore。
5. 生成一个包含扫描元数据和完整路径列表的报告文件。
"""
import fnmatch
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import List, Tuple

from dotenv import load_dotenv

SEPARATOR = "=" * 80


class PathScanner:
    """
    封装了扫描和保存文件/目录路径所有逻辑的类。
    """
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

    def select_target_from_files_dir(self) -> Tuple[List[str], List[str]]:
        """从 ./files/ 目录选择一个或多个txt文件，并读取其中的扫描目标和排除规则。"""
        files_dir = Path('./files')

        if not files_dir.exists() or not any(files_dir.iterdir()):
            print("[INFO] ./files/ 目录不存在或为空，将使用手动输入模式。", file=sys.stderr)
            return [], []

        txt_files = sorted(list(files_dir.glob('*.txt')))
        if not txt_files:
            print("[INFO] ./files/ 目录下没有找到txt文件，将使用手动输入模式。", file=sys.stderr)
            return [], []

        print("发现以下扫描目标配置文件：", file=sys.stderr)
        for i, txt_file in enumerate(txt_files, 1):
            print(f"{i:2d}. {txt_file.name}", file=sys.stderr)
        print(" 0. 手动输入路径", file=sys.stderr)

        while True:
            try:
                choice_str = input("请选择文件编号（可输入多个，用空格分隔；0为手动输入）: ").strip()
                if not choice_str: continue

                choices = choice_str.split()
                if '0' in choices:
                    print("[INFO] 已选择手动输入模式。", file=sys.stderr)
                    return [], []

                selected_indices = set()
                invalid_choices = []
                for c in choices:
                    try:
                        choice_num = int(c)
                        if 1 <= choice_num <= len(txt_files):
                            selected_indices.add(choice_num - 1)
                        else:
                            invalid_choices.append(c)
                    except ValueError:
                        invalid_choices.append(c)

                if invalid_choices:
                    print(f"[ERROR] 输入无效或超出范围: {', '.join(invalid_choices)}", file=sys.stderr)
                    continue
                if not selected_indices: continue

                all_targets, all_excludes = [], []
                selected_names = [txt_files[i].name for i in sorted(list(selected_indices))]
                for index in sorted(list(selected_indices)):
                    targets, excludes = self._parse_targets_and_excludes_from_file(txt_files[index])
                    all_targets.extend(targets)
                    all_excludes.extend(excludes)

                print(f"[INFO] 已选择并汇总文件: {', '.join(selected_names)}", file=sys.stderr)
                # 去重并保留顺序
                unique_targets = list(dict.fromkeys(all_targets))
                unique_excludes = list(dict.fromkeys(all_excludes))
                print(f"[INFO] 汇总后共 {len(unique_targets)} 个扫描目标，{len(unique_excludes)} 个排除规则。", file=sys.stderr)
                return unique_targets, unique_excludes

            except KeyboardInterrupt:
                print("\n已取消选择，将使用手动输入模式。", file=sys.stderr)
                return [], []

    def _parse_targets_and_excludes_from_file(self, file_path: Path) -> Tuple[List[str], List[str]]:
        """从文件中读取扫描目标和排除规则。"""
        try:
            with file_path.open('r', encoding='utf-8', errors='replace') as f:
                lines = f.read().splitlines()
            targets, excludes = [], []
            for line in lines:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                if line.startswith('!'):
                    excludes.append(line[1:])
                else:
                    targets.append(line)
            print(f"[INFO] 从 {file_path.name} 读取到 {len(targets)} 个目标，{len(excludes)} 个排除规则。", file=sys.stderr)
            return targets, excludes
        except Exception as e:
            print(f"[ERROR] 读取文件 {file_path} 失败: {e}", file=sys.stderr)
            return [], []

    def read_targets_from_stdin(self) -> List[str]:
        """从标准输入读取扫描目标和排除规则。"""
        print(
            "请输入要扫描的文件/文件夹路径（每行一个），\n"
            "使用!开头表示排除规则，结束后按 Ctrl+Z ↵ (Win)或 Ctrl+D (Linux/macOS)：",
            file=sys.stderr,
        )
        try:
            return [line.strip() for line in sys.stdin if line.strip() and not line.strip().startswith('#')]
        except KeyboardInterrupt:
            print("\n操作已取消。", file=sys.stderr)
            return []

    def parse_stdin_inputs(self, raw_inputs: List[str]) -> Tuple[List[str], List[str]]:
        """从stdin输入中分离扫描目标和排除规则。"""
        targets, excludes = [], []
        for line in raw_inputs:
            if line.startswith('!'):
                excludes.append(line[1:])
            else:
                targets.append(line)
        return targets, excludes

    def load_gitignore_patterns(self) -> List[str]:
        """加载项目根目录及其父目录中的.gitignore文件的排除模式。"""
        if not self.use_gitignore:
            return []
        patterns = []
        current_dir = self.project_root
        # 向上遍历到文件系统根目录
        while current_dir.parent != current_dir:
            gitignore_path = current_dir / '.gitignore'
            if gitignore_path.is_file():
                try:
                    with gitignore_path.open('r', encoding='utf-8', errors='replace') as f:
                        lines = f.read().splitlines()
                    new_patterns = [line.strip() for line in lines if line.strip() and not line.strip().startswith('#')]
                    patterns.extend(new_patterns)
                    print(f"[INFO] 从 {gitignore_path} 加载了 {len(new_patterns)} 条排除规则。", file=sys.stderr)
                except Exception as e:
                    print(f"[WARNING] 读取 {gitignore_path} 失败: {e}", file=sys.stderr)
            current_dir = current_dir.parent
        return patterns

    def get_all_exclude_patterns(self, custom_excludes: List[str] = None) -> List[str]:
        """整合所有来源的排除模式。"""
        patterns = self.default_exclude_patterns.copy()
        patterns.extend(self.load_gitignore_patterns())
        if custom_excludes:
            patterns.extend(custom_excludes)
        return list(dict.fromkeys(patterns)) # 去重

    def should_exclude(self, path: Path, exclude_patterns: List[str]) -> bool:
        """检查给定的路径是否应该根据排除模式列表被排除。"""
        try:
            rel_path = path.relative_to(self.project_root)
        except ValueError:
            rel_path = path # 如果不在项目根目录下，则使用其绝对路径

        # 将Windows路径分隔符统一为/
        path_str = str(rel_path.as_posix())

        for pattern in exclude_patterns:
            # fnmatch支持gitignore风格的匹配
            if fnmatch.fnmatch(path_str, pattern) or fnmatch.fnmatch(path.name, pattern):
                return True
            # 支持目录匹配 (e.g., 'dist/')
            if pattern.endswith('/') and (path_str + '/').startswith(pattern):
                return True
        return False

    def collect_paths(self, targets: List[str], exclude_patterns: List[str]) -> List[Path]:
        """根据目标列表和排除规则，收集所有符合条件的路径。"""
        collected_paths = set()
        for target_str in targets:
            path = Path(target_str)
            if not path.is_absolute():
                path = self.project_root / path

            if not path.exists():
                print(f"[WARNING] 扫描目标不存在: {path}", file=sys.stderr)
                continue

            # 总是先检查目标本身是否被排除
            if self.should_exclude(path, exclude_patterns):
                print(f"[EXCLUDED] 目标本身被排除: {path}", file=sys.stderr)
                continue

            collected_paths.add(path)

            if path.is_dir():
                print(f"[INFO] 正在扫描目录: {path}", file=sys.stderr)
                for item in path.rglob('*'):
                    if not self.should_exclude(item, exclude_patterns):
                        collected_paths.add(item)
                    # 注释掉以下行以避免在排除大量文件时刷屏
                    # else:
                    #     print(f"[EXCLUDED] {item.relative_to(self.project_root)}", file=sys.stderr)

        return sorted(list(collected_paths))

    def ensure_temp_dir(self, temp_dir: Path) -> None:
        """确保temp目录存在。"""
        try:
            temp_dir.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            print(f"[ERROR] 无法创建目录 {temp_dir}: {e}", file=sys.stderr)
            sys.exit(1)

    def save_path_report(self, paths: List[Path], out_path: Path, original_targets: List[str],
                         exclude_rules: List[str], all_exclude_patterns: List[str]) -> None:
        """将收集到的路径列表和元数据保存到报告文件。"""
        with out_path.open("w", encoding="utf-8") as fout:
            # 写入文件头信息
            fout.write(f"{SEPARATOR}\n")
            fout.write("路径扫描报告\n")
            fout.write(f"{SEPARATOR}\n")
            fout.write(f"报告生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            fout.write(f"项目根目录: {self.project_root}\n")
            fout.write(f"扫描到的路径总数: {len(paths)}\n")
            fout.write("\n")

            # 写入原始输入
            fout.write("原始扫描目标:\n")
            for i, target in enumerate(list(dict.fromkeys(original_targets)), 1):
                fout.write(f"  {i}. {target}\n")
            if exclude_rules:
                fout.write("自定义排除规则:\n")
                for i, rule in enumerate(list(dict.fromkeys(exclude_rules)), 1):
                    fout.write(f"  {i}. !{rule}\n")
            fout.write("\n")

            # 写入所有生效的排除规则
            fout.write(f"所有生效的排除规则 ({len(all_exclude_patterns)}条):\n")
            for i, pattern in enumerate(all_exclude_patterns, 1):
                fout.write(f"  {i:3d}. {pattern}\n")
            fout.write("\n")

            # 写入扫描到的路径列表
            fout.write(f"{SEPARATOR}\n")
            fout.write("扫描到的路径列表 (相对于项目根目录)\n")
            fout.write(f"{SEPARATOR}\n")
            if not paths:
                fout.write("未找到任何符合条件的路径。\n")
            else:
                for i, p in enumerate(paths, 1):
                    try:
                        rel_path = p.relative_to(self.project_root)
                    except ValueError:
                        rel_path = p # 不在根目录下，显示绝对路径
                    fout.write(f"{i:4d}. {rel_path.as_posix()}\n")

        print(f"[OK] 已将 {len(paths)} 条路径保存到 {out_path}", file=sys.stderr)
        print(f"     绝对路径: {out_path.resolve()}", file=sys.stderr)


def main():
    scanner = PathScanner()

    print(f"[INFO] 项目根目录: {scanner.project_root}", file=sys.stderr)
    if scanner.use_gitignore:
        print("[INFO] 已启用.gitignore支持。", file=sys.stderr)
    else:
        print("[INFO] 未启用.gitignore支持。", file=sys.stderr)

    # 1. 获取输入
    targets, custom_excludes = scanner.select_target_from_files_dir()
    if not targets and not custom_excludes:
        raw_inputs = scanner.read_targets_from_stdin()
        if raw_inputs:
            targets, custom_excludes = scanner.parse_stdin_inputs(raw_inputs)

    if not targets:
        print("未提供任何扫描目标，脚本退出。", file=sys.stderr)
        return

    # 2. 整合排除规则并扫描
    all_exclude_patterns = scanner.get_all_exclude_patterns(custom_excludes)
    collected_paths = scanner.collect_paths(targets, all_exclude_patterns)

    if not collected_paths:
        print("根据指定的扫描目标和排除规则，没有找到任何路径。", file=sys.stderr)
        return

    print(f"[INFO] 扫描完成，共找到 {len(collected_paths)} 条路径。", file=sys.stderr)

    # 3. 准备输出
    temp_dir = Path("./temp")
    scanner.ensure_temp_dir(temp_dir)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_file = temp_dir / f"path_report_{timestamp}.txt"

    # 4. 保存报告
    scanner.save_path_report(collected_paths, out_file, targets, custom_excludes, all_exclude_patterns)


if __name__ == "__main__":
    main()
