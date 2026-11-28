#!/usr/bin/env python3
"""Optional owner-operated KDE/X11 review, not COSMIC or release evidence."""
import json
import os
import pathlib
import platform
import re
import stat
import subprocess
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "gpui-candidate/tools"))
from owner_cosmic_review import (ROOT, NAME, CHECKS, STATUS, require, command,
    source_identity, sha, parse_answers, review_exit_code, run_review_process)

FIELDS = ("User", "Desktop", "Type", "Remote", "Active")

def verify_session(uid, euid, system, machine, env, query):
    require(uid > 0 and uid == euid, "必须使用普通登录用户")
    require(system.get("ID") == "pop" and system.get("VERSION_ID") == "24.04"
            and machine == "x86_64", "仅限 Pop!_OS 24.04 x86_64")
    desktop = env.get("XDG_CURRENT_DESKTOP", "")
    require(len(desktop) <= 256 and "kde" in [x.casefold() for x in desktop.split(":")]
            and env.get("XDG_SESSION_TYPE") == "x11", "必须在 KDE X11 本地会话运行")
    require(not env.get("WAYLAND_DISPLAY") and not env.get("WAYLAND_SOCKET"), "存在 Wayland 会话标识，停止")
    require(env.get("XDG_RUNTIME_DIR") == f"/run/user/{uid}", "非标准用户 runtime 目录")
    match = re.fullmatch(r":([0-9]{1,4})(?:\.[0-9]{1,2})?", env.get("DISPLAY", ""))
    require(match is not None, "必须使用本地 X11 DISPLAY")
    session = env.get("XDG_SESSION_ID", "")
    require(re.fullmatch(r"[A-Za-z0-9]{1,32}", session) is not None, "无法确定本地登录会话")
    raw = query(session)
    require(len(raw) <= 4096, "会话元数据超量")
    fields = {}
    for line in raw.splitlines():
        name, sep, value = line.partition("=")
        require(sep and name in FIELDS and name not in fields, "会话元数据无效")
        fields[name] = value
    require(set(fields) == set(FIELDS) and fields["User"] == str(uid)
            and fields["Desktop"].casefold() == "kde" and fields["Type"] == "x11"
            and fields["Remote"] == "no" and fields["Active"] == "yes",
            "logind 未确认当前普通用户的本地活动 KDE X11 会话")
    return pathlib.Path("/tmp/.X11-unix") / ("X" + match[1])

def loginctl_query(session):
    result = subprocess.run(["/usr/bin/loginctl", "show-session", session, "--no-pager",
        *["--property="+name for name in FIELDS]], check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=5,
        env={"PATH":"/usr/bin:/bin","LANG":"C.UTF-8"})
    return result.stdout

def check_nodes(socket, uid):
    runtime = pathlib.Path(f"/run/user/{uid}").lstat()
    require(stat.S_ISDIR(runtime.st_mode) and runtime.st_uid == uid
            and stat.S_IMODE(runtime.st_mode) == 0o700, "runtime 目录异常")
    node = socket.lstat()
    require(stat.S_ISSOCK(node.st_mode) and node.st_uid in (0, uid), "本地 X11 socket 异常")

def checked_source():
    head = source_identity()
    path = "../tools/owner_kde_x11_review.py"
    require(not command(["git","status","--porcelain","--untracked-files=normal","--",path]),
            "KDE 检查工具存在本地改动")
    require(command(["git","ls-files","--error-unmatch",path]), "KDE 检查工具未纳入版本控制")
    return head

def report(head, lock, binary, code, answers, method):
    review_exit_code(code, answers, method)
    return {"schema":"lanveriq.gpui.owner-kde-x11-review-v1",
        "source_sha":head,"lock_sha256":lock,"elf_sha256":binary,
        "target":"Pop!_OS 24.04 x86_64","session_claim":"KDE_X11",
        "local_active_logind_metadata":"CHECKED","x11_socket_metadata":"CHECKED",
        "exit_code":code,"ctrl_q":STATUS["p"] if code == 0 and method == "y" else "NOT_CONFIRMED",
        "checks":answers,"chinese_ime_composition":answers["chinese_ime"],
        "cosmic_wayland":"NOT_RUN","native_compositor_protocol_verified":False,
        "window_manager_close":"NOT_TESTED","hardware_gpu":"NOT_VERIFIED",
        "independent_l3":"NOT_APPROVED","install_authorized":False,"release_approved":False}

def main():
    require(sys.stdin.isatty(), "必须由设备所有人在交互终端运行")
    uid = os.getuid()
    def session():
        sock = verify_session(uid, os.geteuid(), platform.freedesktop_os_release(),
                              platform.machine(), os.environ, loginctl_query)
        check_nodes(sock, uid)
        return sock
    sock = session()
    head = checked_source()
    lock = ROOT/"Cargo.lock"
    expected = (ROOT/"Cargo.lock.sha256").read_text().split()[0]
    require(re.fullmatch(r"[0-9a-f]{64}", expected) and sha(lock) == expected, "Cargo.lock 校验失败")
    print("仅 KDE/X11 所有人观察，不是 COSMIC Wayland 验收。不会安装 Agent、修改服务或上传报告。")
    for i, (_, description) in enumerate(CHECKS, 1):
        print(f"{i}. {description}")
    print("完成后 Ctrl+Q；缺少依赖时停止，不自动下载。", flush=True)
    run_review_process(["cargo","+1.100.0","build","--locked","--offline","--release",
        "--target-dir",str(ROOT/"target"),"--bin",NAME], phase="离线构建", cwd=ROOT, check=True, timeout=3600)
    require(checked_source() == head and sha(lock) == expected and session() == sock,
            "构建期间源码、锁或会话发生变化")
    binary = ROOT/"target/release"/NAME
    require(binary.is_file() and not binary.is_symlink(), "Release 可执行文件缺失")
    binary_sha = sha(binary)
    env = dict(os.environ)
    for name in ("WAYLAND_DISPLAY","WAYLAND_SOCKET"):
        env.pop(name,None)
    result = run_review_process([str(binary)],phase="窗口验收",cwd=ROOT,env=env,timeout=900)
    require(checked_source() == head and sha(lock) == expected and sha(binary) == binary_sha
            and session() == sock, "测试期间源码、锁、产物或会话发生变化")
    print("窗口退出码:", result.returncode)
    print("按上述 9 项输入 p=通过 f=失败 n=未测")
    answers = parse_answers(input("> "))
    method = input("是否确实使用 Ctrl+Q 退出？ y/n: ").strip().lower()
    output = report(head,expected,binary_sha,result.returncode,answers,method)
    print("=== KDE/X11 脱敏所有人观察报告（非签名，COSMIC NOT RUN） ===")
    print(json.dumps(output, ensure_ascii=False, indent=2, sort_keys=True))
    return review_exit_code(result.returncode,answers,method)

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.SubprocessError, EOFError, KeyboardInterrupt) as error:
        print("STOP:", str(error) if isinstance(error, ValueError) else "本机准备或运行失败/取消；未生成通过证据。", file=sys.stderr)
        sys.exit(1)
