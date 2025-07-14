
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/uio.h>
#include <android/log.h>
#include <asm-generic/unistd.h>

/* Unshare the file descriptor table before closing file descriptors. */
#define CLOSE_RANGE_UNSHARE	(1U << 1)
/* Set the FD_CLOEXEC bit instead of closing the file descriptor. */
#define CLOSE_RANGE_CLOEXEC	(1U << 2)

extern "C" __attribute__((visibility("default"))) int close_range(unsigned int first, unsigned int last, int flags)
{
    if (first > last)
    {
        errno = EINVAL;
        return -1;
    }
    for (unsigned int fd = first; fd <= last; fd++)
    {
        if (flags & CLOSE_RANGE_CLOEXEC)
        {
            int flags = fcntl(fd, F_GETFD, 0);
            if (flags == -1)
            {
                return -1;
            }

            if (fcntl(fd, F_SETFD, flags | FD_CLOEXEC) != 0)
            {
                return -1;
            }
        }
        else
        {
            //ignoring CLOSE_RANGE_UNSHARE for now
            if (close(fd) != 0)
            {
                return -1;
            }
        }
    }

    return 0;
}

extern "C" __attribute__((visibility("default"))) ssize_t copy_file_range(int fd_in, off_t *_Nullable off_in,
                        int fd_out, off_t *_Nullable off_out,
                        size_t size, unsigned int flags)
{
    return splice(fd_in, off_in, fd_out, off_out, size, 0);
}

extern "C" __attribute__((visibility("default"))) ssize_t preadv2(int fd, const struct iovec *iov, int iovcnt,
                                                                  off_t offset, int flags)
{
    return syscall(__NR_preadv2, (unsigned long)fd, iov, (unsigned long)iovcnt, (unsigned long)(offset & 0xffffffff), (unsigned long)(offset >> 32), flags);
}

extern "C" __attribute__((visibility("default"))) ssize_t pwritev2(int fd, const struct iovec *iov, int iovcnt,
                 off_t offset, int flags)
{
    return syscall(__NR_pwritev2, (unsigned long)fd, iov, (unsigned long)iovcnt, (unsigned long)(offset & 0xffffffff), (unsigned long)(offset >> 32), flags);
}

extern "C" __attribute__((visibility("default"))) int backtrace(void *buffer, int size)
{
    return 0;
}
