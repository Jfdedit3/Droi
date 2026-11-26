#define _GNU_SOURCE
#include <fcntl.h>
#include <linux/stat.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    struct statx sx = {0};
    int dir = -1, file = -1, rc = 1;
    char name[96], buf[4] = {0};
    snprintf(name, sizeof name, ".droi-probe-%ld", (long)getpid());
    if (syscall(SYS_statx, AT_FDCWD, argv[1], 0, STATX_BASIC_STATS, &sx) < 0 ||
        !S_ISDIR(sx.stx_mode)) { perror("statx(path)"); goto out; }
    dir = open(argv[1], O_RDONLY | O_DIRECTORY);
    if (dir < 0) { perror("open(directory)"); goto out; }
    if (syscall(SYS_statx, dir, "", AT_EMPTY_PATH, STATX_BASIC_STATS, &sx) < 0 ||
        !S_ISDIR(sx.stx_mode)) { perror("statx(AT_EMPTY_PATH)"); goto out; }
    file = openat(dir, name, O_CREAT | O_EXCL | O_RDWR, 0600);
    if (file < 0) { perror("openat"); goto out; }
    if (write(file, "ok", 2) != 2 || lseek(file, 0, SEEK_SET) < 0 ||
        read(file, buf, 2) != 2 || strcmp(buf, "ok")) { perror("read/write"); goto out; }
    puts("PASS: statx(path), statx(fd), openat, read/write");
    rc = 0;
out:
    if (file >= 0) { close(file); unlinkat(dir, name, 0); }
    if (dir >= 0) close(dir);
    return rc;
}
