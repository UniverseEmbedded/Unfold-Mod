#!/usr/bin/env python3
"""
read_files_to_stdout.py
增强版文件读取脚本，支持：
1. 从 ./files/ 目录选择txt文件作为路径列表
2. txt文件中使用!开头的行作为排除规则（类似gitignore）
3. 文件夹递归读取和gitignore式排除功能
4. .env配置项目根目录和是否读取gitignore
"""
import fnmatch
import os
import sys
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
        """从 ./files/ 目录选择txt文件并读取其中的路径和排除规则"""
        files_dir = Path('./files')

        if not files_dir.exists():
            print("[INFO] ./files/ 目录不存在，将使用手动输入模式", file=sys.stderr)
            return [], []

        txt_files = list(files_dir.glob('*.txt'))

        if not txt_files:
            print("[INFO] ./files/ 目录下没有找到txt文件，将使用手动输入模式", file=sys.stderr)
            return [], []

        print("发现以下txt文件：", file=sys.stderr)
        for i, txt_file in enumerate(txt_files, 1):
            print(f"{i:2d}. {txt_file.name}", file=sys.stderr)

        print("0. 手动输入路径", file=sys.stderr)

        while True:
            try:
                choice = input("请选择文件编号（0为手动输入）: ")
                choice_num = int(choice)

                if choice_num == 0:
                    return [], []
                elif 1 <= choice_num <= len(txt_files):
                    selected_file = txt_files[choice_num - 1]
                    return self._parse_paths_and_excludes_from_file(selected_file)
                else:
                    print(f"请输入 0-{len(txt_files)} 之间的数字", file=sys.stderr)
            except (ValueError, KeyboardInterrupt):
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
                        print(f"[EXCLUDED] 文件: {file_path}", file=sys.stderr)

        return sorted(set(all_files))  # 去重并排序

    def dump_file(self, path: Path) -> None:
        """读取文件并输出到stdout"""
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except Exception as exc:
            print(f"[ERROR] 无法读取 {path}: {exc}", file=sys.stderr)
            return

        print(f"{SEPARATOR}\n{path}\n{SEPARATOR}")
        print(text, end="" if text.endswith("\n") else "\n")


def main():
    processor = FileProcessor()

    print(f"[INFO] 项目根目录: {processor.project_root}", file=sys.stderr)
    if processor.use_gitignore:
        print("[INFO] 已启用gitignore支持", file=sys.stderr)

    # 首先尝试从files目录选择
    paths, file_excludes = processor.select_txt_file_from_files_dir()

    # 如果没有选择文件，则手动输入
    if not paths:
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
        print("没有找到任何需要处理的文件。", file=sys.stderr)
        return

    print(f"[INFO] 共找到 {len(files)} 个文件需要处理", file=sys.stderr)

    # 处理每个文件
    for file_path in files:
        processor.dump_file(file_path)


if __name__ == "__main__":
    main()
