#!/usr/bin/env python3
"""Owner-operated, nonprivileged, local-only review of the GPUI candidate.
Requires an existing trusted checkout and cached Rust/toolchain/dependencies.
This is NOT an installer, signed evidence, full L3, or release approval.
"""
import hashlib
import json
import os
import pathlib
import platform
import re
import stat
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NAME = "lanveriq-desktop-gpui-candidate"
CHECKS = (
    ("window", "窗口出现，中文标签可读，没有白屏或崩溃"),
    ("navigation", "七项导航均可切换，页面标题与内容正确"),
    ("configuration", "选择 15/30/60/120/300 秒草稿；离开再返回重置为 30 秒，不保存"),
    ("metadata", "手动检测结束，显示系统/包/服务/图形元数据；没有权限弹窗"),
    ("setup", "逐项阅读说明，离开再返回重置；安装及其他受限按钮保持禁用"),
    ("diagnostics", "诊断文字可选择，安装/启动授权为 false、生产功能 OFF"),
    ("resize_scroll", "窗口缩放和小窗口内滚动正常，无内容溢出遮挡"),
    ("keyboard", "Tab/Shift+Tab 与 Enter/Space 焦点和导航正常，禁用按钮不可触发"),
    ("chinese_ime", "关于页使用真实中文输入法组合并确认“中文输入测试”（非粘贴）；检查候选/光标、清空、离开再进入为空；无输入法选 n"),
)
STATUS = {"p": "OWNER_REPORTED_PASS", "f": "OWNER_REPORTED_FAIL", "n": "NOT_TESTED"}

def require(ok, message):
    if not ok:
        raise ValueError(message)

def verify_session(uid, euid, system, machine, env):
    require(uid > 0 and uid == euid, "必须使用普通登录用户")
    require(system.get("ID") == "pop" and system.get("VERSION_ID") == "24.04"
            and machine == "x86_64", "仅限 Pop!_OS 24.04 x86_64")
    desktop = env.get("XDG_CURRENT_DESKTOP", "")
    require(len(desktop) <= 256 and any(part.casefold() == "cosmic" for part in desktop.split(":"))
            and env.get("XDG_SESSION_TYPE") == "wayland", "必须在 COSMIC Wayland 本地会话运行")
    name = env.get("WAYLAND_DISPLAY", "")
    require(re.fullmatch(r"wayland-[0-9]{1,8}", name) is not None, "非标准 Wayland socket 名，停止")
    require(env.get("XDG_RUNTIME_DIR") == f"/run/user/{uid}", "非标准用户 runtime 目录，停止")
    return pathlib.Path(f"/run/user/{uid}") / name

def verify_socket(path, uid):
    directory = path.parent.lstat()
    node = path.lstat()
    require(stat.S_ISDIR(directory.st_mode) and directory.st_uid == uid
            and stat.S_IMODE(directory.st_mode) == 0o700, "runtime 目录所有权或模式异常")
    require(stat.S_ISSOCK(node.st_mode) and node.st_uid == uid, "Wayland socket 不存在或所有权异常")

def command(args):
    result = subprocess.run(args, cwd=ROOT, check=True, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15)
    return result.stdout.strip()

def source_identity():
    head = command(["git", "rev-parse", "HEAD"])
    require(re.fullmatch(r"[0-9a-f]{40}", head) is not None, "无法确定源码提交")
    # Only candidate subtree; unrelated local server secrets/config are not read.
    require(not command(["git", "status", "--porcelain", "--untracked-files=normal", "--", "."]),
            "候选目录存在本地改动或未跟踪文件，请先人工检查；不自动清理")
    return head

def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def parse_answers(answer):
    words = answer.lower().split()
    require(len(words) == len(CHECKS) and all(word in STATUS for word in words),
            "请按顺序输入 9 个 p/f/n")
    return {key: STATUS[word] for (key, _), word in zip(CHECKS, words)}

def wayland_env(env):
    result = dict(env)
    # No X11 fallback or inherited Wayland socket fd; use the verified named socket.
    for name in ("DISPLAY", "XAUTHORITY", "WAYLAND_SOCKET"):
        result.pop(name, None)
    return result

def main():
    require(sys.stdin.isatty(), "必须由设备所有人在交互式终端启动")
    uid = os.getuid()
    socket = verify_session(uid, os.geteuid(), platform.freedesktop_os_release(),
                            platform.machine(), os.environ)
    verify_socket(socket, uid)
    head = source_identity()
    lock = ROOT / "Cargo.lock"
    expected = (ROOT / "Cargo.lock.sha256").read_text().split()[0]
    require(re.fullmatch(r"[0-9a-f]{64}", expected) and sha(lock) == expected, "Cargo.lock 校验失败")
    print("仅本机候选窗口测试；不会安装 Agent、修改服务或上传报告。")
    print("以下检查仅凭所有人观察，不会自动标记 L3 PASS。")
    for i, (_, description) in enumerate(CHECKS, 1):
        print(f"{i}. {description}")
    print("最后使用 Ctrl+Q 退出；中文 IME 必须实际组合并确认，不能仅粘贴或凭中文标签判断。")
    print("开始离线编译。缺依赖时停止，不自动下载或安装。", flush=True)
    subprocess.run(["cargo", "+1.100.0", "build", "--locked", "--offline", "--release",
                    "--target-dir", str(ROOT / "target"), "--bin", NAME],
                   cwd=ROOT, check=True, timeout=3600)
    require(source_identity() == head and sha(lock) == expected, "构建期间源码或锁发生变化")
    binary = ROOT / "target" / "release" / NAME
    require(binary.is_file() and not binary.is_symlink(), "Release 可执行文件缺失")
    binary_sha = sha(binary)
    verify_socket(socket, uid)
    print("打开原生 Wayland 候选窗口（已移除 X11 DISPLAY）；完成后 Ctrl+Q。", flush=True)
    result = subprocess.run([str(binary)], cwd=ROOT, env=wayland_env(os.environ), timeout=900)
    require(sha(binary) == binary_sha and source_identity() == head, "测试期间产物或源码发生变化")
    print("窗口退出码:", result.returncode)
    print("按上述 9 项顺序输入 p=通过 f=失败 n=未测，例如 p p n n p p p n n")
    answers = parse_answers(input("> "))
    method = input("是否确实使用 Ctrl+Q 退出？ y/n: ").strip().lower()
    require(method in ("y", "n"), "退出方式必须选择 y/n")
    report = {
        "schema": "lanveriq.gpui.owner-cosmic-review-v2",
        "source_sha": head, "lock_sha256": expected, "elf_sha256": binary_sha,
        "target": "Pop!_OS 24.04 x86_64", "session_claim": "COSMIC_WAYLAND",
        "wayland_socket_metadata": "CHECKED", "x11_display_removed": True,
        "exit_code": result.returncode, "ctrl_q": STATUS["p"] if result.returncode == 0 and method == "y" else "NOT_CONFIRMED",
        "checks": answers, "native_compositor_protocol_verified": False,
        "chinese_ime_composition": answers["chinese_ime"],
        "window_manager_close": "NOT_TESTED", "hardware_gpu": "NOT_VERIFIED",
        "independent_l3": "NOT_APPROVED", "install_authorized": False, "release_approved": False,
    }
    print("=== 可复制的脱敏所有人观察报告（非签名） ===")
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if result.returncode or any(v == STATUS["f"] for v in answers.values()):
        return 1
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.SubprocessError, EOFError, KeyboardInterrupt) as error:
        # No raw OS/runtime path, child output, or environment is copied into the report.
        if isinstance(error, ValueError):
            print("STOP:", str(error), file=sys.stderr)
        else:
            print("STOP: 本机准备或运行失败/取消；未生成通过证据。", file=sys.stderr)
        sys.exit(1)
