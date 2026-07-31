#!/usr/bin/env python3
"""Flyway 마이그레이션 정적 검증 스크립트.

DB 없이 파일명/버전/git 이력만으로 다음을 검사한다.

ERROR (CI 실패)
  1. Flyway가 인식하지 못하는 파일명  -> 마이그레이션이 조용히 누락된다
  2. 버전 번호 중복                    -> Flyway 부팅 자체가 실패한다
  3. 기존 마이그레이션 파일의 수정/삭제/이름변경
                                       -> 이미 적용된 DB에서 체크섬 검증이 깨져 배포가 막힌다

WARNING (CI 통과, 로그만 남김)
  4. 순서 역행(out-of-order) 마이그레이션
                                       -> spring.flyway.out-of-order 가 false면 배포가 막힌다

환경 변수
  MIGRATION_DIR : 마이그레이션 디렉터리 (기본: pickup/src/main/resources/db/migration)
  BASE_REF      : PR의 base 브랜치 이름. 비어 있으면 git 기반 검사(3, 4)를 건너뛴다.
"""

import os
import re
import subprocess
import sys
from collections import defaultdict

MIGRATION_DIR = os.environ.get(
    "MIGRATION_DIR", "pickup/src/main/resources/db/migration"
)
BASE_REF = os.environ.get("BASE_REF", "").strip()

# Flyway 기본 규칙: 접두사 V(버전) / R(반복 실행), 구분자 __, 접미사 .sql (모두 대소문자 구분)
VERSIONED = re.compile(r"^V(?P<version>\d+([._]\d+)*)__(?P<description>.+)\.sql$")
REPEATABLE = re.compile(r"^R__(?P<description>.+)\.sql$")

errors = []
warnings = []


def error(message, file=None):
    errors.append((message, file))


def warn(message, file=None):
    warnings.append((message, file))


def git(*args):
    """git 명령을 실행하고 stdout을 반환한다. 실패하면 None."""
    try:
        result = subprocess.run(
            ["git", *args], capture_output=True, text=True, check=True
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    return result.stdout.strip()


def normalize(version):
    """Flyway의 버전 비교 규칙에 맞춰 정규화한다.

    Flyway는 '.'과 '_'를 동일한 구분자로 보고, 빠진 자리는 0으로 채워 비교한다.
    따라서 V2.5 / V2_5 / V2.5.0 은 모두 같은 버전 2.5 로 취급된다.
    """
    parts = [int(p) for p in re.split(r"[._]", version)]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)


def format_version(parts):
    return ".".join(str(p) for p in parts)


def collect_files(directory):
    """디렉터리를 재귀 탐색해 레포 기준 상대 경로 목록을 반환한다."""
    found = []
    for root, _, filenames in os.walk(directory):
        for filename in filenames:
            found.append(os.path.join(root, filename))
    return sorted(found)


# ---------------------------------------------------------------------------
# 1) 파일명 규칙 + 2) 버전 중복
# ---------------------------------------------------------------------------
def check_filenames_and_versions(files):
    """파일명 규칙을 검사하고, 정상 파일의 {정규화 버전: [경로]} 맵을 반환한다."""
    versions = defaultdict(list)

    for path in files:
        filename = os.path.basename(path)

        versioned = VERSIONED.match(filename)
        if versioned:
            versions[normalize(versioned.group("version"))].append(path)
            continue

        if REPEATABLE.match(filename):
            continue

        # 아래부터는 Flyway가 무시하거나 거부하는 파일. 원인을 구체적으로 알려준다.
        if not filename.endswith(".sql"):
            error(
                f"'{filename}' 은 .sql 로 끝나지 않아 Flyway가 무시합니다. "
                "마이그레이션 디렉터리에서 제거하거나 확장자를 소문자 .sql 로 맞춰주세요.",
                path,
            )
        elif filename[:1].lower() == "v" and filename[:1] != "V":
            error(
                f"'{filename}' 의 접두사가 소문자입니다. Flyway는 대문자 'V'만 인식합니다.",
                path,
            )
        elif "__" not in filename:
            error(
                f"'{filename}' 에 구분자 '__'(밑줄 2개)가 없습니다. "
                "형식은 V{버전}__{설명}.sql 입니다.",
                path,
            )
        else:
            error(
                f"'{filename}' 은 Flyway 파일명 규칙(V{{버전}}__{{설명}}.sql)에 맞지 않습니다.",
                path,
            )

    for version, paths in sorted(versions.items()):
        if len(paths) > 1:
            listed = ", ".join(os.path.basename(p) for p in paths)
            error(
                f"버전 {format_version(version)} 이 중복됩니다: {listed}. "
                "Flyway는 같은 버전이 둘 이상이면 애플리케이션 부팅 단계에서 실패합니다.",
                paths[0],
            )

    return versions


# ---------------------------------------------------------------------------
# 3) 기존 파일 수정/삭제 + 4) 순서 역행
# ---------------------------------------------------------------------------
def check_history(versions):
    if not BASE_REF:
        print(
            "NOTICE: BASE_REF 가 없어 git 기반 검사(기존 파일 수정 감지, 순서 역행 감지)를 "
            "건너뜁니다."
        )
        return

    # BASE_REF 가 주어진 이상 git 검사는 반드시 수행돼야 한다.
    # 조용히 건너뛰면 체크섬 검사가 무력화되므로 실패로 처리한다.
    merge_base = git("merge-base", f"origin/{BASE_REF}", "HEAD")
    if not merge_base:
        error(
            f"origin/{BASE_REF} 와의 merge-base 를 찾을 수 없어 기존 파일 수정 감지를 "
            "수행할 수 없습니다. checkout 시 fetch-depth: 0 인지 확인해주세요."
        )
        return

    diff = git(
        "diff", "--name-status", "--find-renames", merge_base, "HEAD", "--", MIGRATION_DIR
    )
    if diff is None:
        error("git diff 실행에 실패해 기존 파일 수정 감지를 수행할 수 없습니다.")
        return

    added = []
    for line in diff.splitlines():
        if not line.strip():
            continue
        fields = line.split("\t")
        status = fields[0]

        if status == "A":
            added.append(fields[1])
        elif status == "M":
            error(
                f"'{os.path.basename(fields[1])}' 은 이미 base 브랜치에 있던 마이그레이션인데 "
                "내용이 수정되었습니다. 이미 적용된 DB에서 체크섬 검증이 깨져 배포가 실패합니다. "
                "수정 대신 새 버전의 마이그레이션을 추가해주세요.",
                fields[1],
            )
        elif status == "D":
            error(
                f"'{os.path.basename(fields[1])}' 이 삭제되었습니다. 이미 적용된 DB에서는 "
                "Flyway가 '적용됐지만 파일이 없는 마이그레이션'으로 판단해 배포가 실패합니다.",
                fields[1],
            )
        elif status.startswith("R"):
            old, new = fields[1], fields[2]
            error(
                f"'{os.path.basename(old)}' 이 '{os.path.basename(new)}' 로 이름이 "
                "변경되었습니다. Flyway는 파일명으로 버전과 설명을 식별하므로 "
                "이미 적용된 DB에서 검증이 깨집니다.",
                new,
            )

    check_out_of_order(merge_base, added, versions)


def check_out_of_order(merge_base, added, versions):
    """새로 추가된 버전이 base의 최대 버전보다 작으면 경고한다."""
    if not added:
        return

    base_listing = git("ls-tree", "-r", "--name-only", merge_base, "--", MIGRATION_DIR)
    if not base_listing:
        return

    base_versions = []
    for path in base_listing.splitlines():
        matched = VERSIONED.match(os.path.basename(path))
        if matched:
            base_versions.append(normalize(matched.group("version")))
    if not base_versions:
        return

    base_max = max(base_versions)

    added_versions = []
    for path in added:
        matched = VERSIONED.match(os.path.basename(path))
        if matched:
            added_versions.append((normalize(matched.group("version")), path))

    for version, path in sorted(added_versions):
        if version < base_max:
            warn(
                f"'{os.path.basename(path)}'(버전 {format_version(version)}) 은 base 브랜치의 "
                f"최신 버전 {format_version(base_max)} 보다 낮습니다. "
                "base가 이미 배포된 상태라면 Flyway는 이 마이그레이션을 순서 역행으로 보고 "
                "배포를 중단합니다(spring.flyway.out-of-order 기본값 false).",
                path,
            )


def sanitize(text, limit=500):
    """Slack 페이로드(YAML)에 끼워 넣어도 깨지지 않는 한 줄 문자열로 만든다.

    개행이 있으면 YAML 블록이 깨지고, 큰따옴표가 있으면 문자열이 조기 종료된다.
    백틱/$/백슬래시는 이후 셸 단계에 노출돼도 안전하도록 함께 제거한다.
    """
    flattened = re.sub(r"\s+", " ", text)
    stripped = re.sub(r'["`$\\]', "", flattened)
    if len(stripped) > limit:
        return stripped[: limit - 3] + "..."
    return stripped


def write_outputs():
    """Slack 알림 스텝이 쓸 수 있도록 결과를 GITHUB_OUTPUT 으로 내보낸다."""
    output_path = os.environ.get("GITHUB_OUTPUT")
    if not output_path:
        return

    # 오류가 많아도 Slack 메시지가 무한정 길어지지 않도록 상위 3건만 싣는다.
    shown = [message for message, _ in errors[:3]]
    if len(errors) > 3:
        shown.append(f"(그 외 {len(errors) - 3}건)")
    summary = " / ".join(shown) if shown else "오류 없음"

    with open(output_path, "a", encoding="utf-8") as f:
        f.write(f"error_count={len(errors)}\n")
        f.write(f"warning_count={len(warnings)}\n")
        f.write(f"error_summary={sanitize(summary)}\n")


def report():
    """GitHub Actions 어노테이션과 스텝 요약으로 결과를 출력한다."""
    for message, file in warnings:
        location = f" file={file}," if file else ""
        print(f"::warning{location}::{message}")
    for message, file in errors:
        location = f" file={file}," if file else ""
        print(f"::error{location}::{message}")

    write_outputs()

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        lines = ["## Flyway 마이그레이션 검증", ""]
        if not errors and not warnings:
            lines.append("모든 검사를 통과했습니다.")
        if errors:
            lines.append(f"### 오류 {len(errors)}건")
            lines += [f"- {message}" for message, _ in errors]
            lines.append("")
        if warnings:
            lines.append(f"### 경고 {len(warnings)}건")
            lines += [f"- {message}" for message, _ in warnings]
        with open(summary_path, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

    if errors:
        print(f"\n검증 실패: 오류 {len(errors)}건, 경고 {len(warnings)}건")
        return 1
    print(f"\n검증 통과: 경고 {len(warnings)}건")
    return 0


def main():
    if not os.path.isdir(MIGRATION_DIR):
        print(f"::error::마이그레이션 디렉터리를 찾을 수 없습니다: {MIGRATION_DIR}")
        return 1

    files = collect_files(MIGRATION_DIR)
    if not files:
        print(f"NOTICE: {MIGRATION_DIR} 에 파일이 없습니다.")
        return 0

    print(f"검사 대상 {len(files)}개 파일 ({MIGRATION_DIR})")
    versions = check_filenames_and_versions(files)
    check_history(versions)
    return report()


if __name__ == "__main__":
    sys.exit(main())
