#include <signal.h>
#include <ucontext.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include "crashhandler.h"

#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))

static bool installed = false;
static struct sigaction prevsa = {};
static char filename[1024] = {};
static char crashbuf[1024] = {};

static const char ascii_digits[] = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
};

unsigned int print_ulong(char * buf, unsigned long v, int pad, bool hex)
{
    unsigned int pos = 0;
    int digits = v == 0 ? 1 : 0;
    int base = hex ? 16 : 10;
    unsigned long power = v == 0 ? base : 1;
    unsigned long v_ = v;
    while(v_ > 0)
    {
        v_ /= base;
        power *= base;
        digits++;
    }

    for (unsigned int i = digits; i < pad; i++)
    {
        buf[pos++] = '0';
    }

    power /= base;
    for (unsigned int i = 0; i < digits; i++)
    {
        buf[pos++] = ascii_digits[(v / power) % base];
        power /= base;
    }
    return pos;
}

void segfault_handler(int sig, siginfo_t *info, void *context) {
    ucontext_t *ucontext = (ucontext_t *)context;

    unsigned int pos = 0;
    crashbuf[pos++] = 'A';
    crashbuf[pos++] = 't';
    crashbuf[pos++] = ' ';

    struct timespec spec = {};
    clock_gettime(CLOCK_REALTIME, &spec);
    pos += print_ulong(crashbuf + pos, spec.tv_sec, 0, false);

    crashbuf[pos++] = '\n';
    crashbuf[pos++] = 'S';
    crashbuf[pos++] = 'i';
    crashbuf[pos++] = 'g';
    crashbuf[pos++] = 'n';
    crashbuf[pos++] = 'a';
    crashbuf[pos++] = 'l';
    crashbuf[pos++] = ':';
    crashbuf[pos++] = ' ';
    pos += print_ulong(crashbuf + pos, info->si_signo, 0, false);
    crashbuf[pos++] = ',';
    crashbuf[pos++] = ' ';
    crashbuf[pos++] = 'C';
    crashbuf[pos++] = 'o';
    crashbuf[pos++] = 'd';
    crashbuf[pos++] = 'e';
    crashbuf[pos++] = ':';
    crashbuf[pos++] = ' ';
    pos += print_ulong(crashbuf + pos, info->si_code, 0, false);
    crashbuf[pos++] = ',';
    crashbuf[pos++] = ' ';
    crashbuf[pos++] = 'A';
    crashbuf[pos++] = 'd';
    crashbuf[pos++] = 'd';
    crashbuf[pos++] = 'r';
    crashbuf[pos++] = ':';
    crashbuf[pos++] = ' ';
    crashbuf[pos++] = '0';
    crashbuf[pos++] = 'x';
    pos += print_ulong(crashbuf + pos, (unsigned long)info->si_addr, 16, true);

    crashbuf[pos++] = '\n';
    crashbuf[pos++] = '\n';
    for (unsigned int i = 0; i < ARRAY_SIZE(ucontext->uc_mcontext.regs); i++)
    {
        crashbuf[pos++] = 'R';
        unsigned int reglen = print_ulong(crashbuf + pos, i, false, false);
        pos += reglen;
        if (reglen == 1)
        {
            crashbuf[pos++] = ' ';
        }
        crashbuf[pos++] = ' ';
        crashbuf[pos++] = '0';
        crashbuf[pos++] = 'x';
        pos += print_ulong(crashbuf + pos, ucontext->uc_mcontext.regs[i], 16, true);

        if (i % 4 == 3)
        {
            crashbuf[pos++] = '\n';
        }
        else
        {
            crashbuf[pos++] = ' ';
        }
    }
    crashbuf[pos++] = '\n';

    int fd = open(filename, O_CREAT | O_WRONLY | O_TRUNC, 0600);
    if (fd != -1)
    {
        int written = 0;
        while (written < pos)
        {
            int ret = write(fd, crashbuf + written, pos - written);
            if (ret <= 0)
            {
                break;
            }
            written += ret;
        }
        close(fd);
    }

    //restore previous handler to crash for real
    sigaction(SIGSEGV, &prevsa, NULL);
}

int install_handler(const char * crashpath)
{
    if (installed)
    {
        return 0;
    }

    if (strlen(crashpath) > sizeof(filename) - 1)
    {
        return 1;
    }
    strcpy(filename, crashpath);

    struct sigaction sa = {};
    sa.sa_flags = SA_SIGINFO;
    sa.sa_sigaction = segfault_handler;
    sigemptyset(&sa.sa_mask);

    if (sigaction(SIGSEGV, &sa, &prevsa) == -1) {
        return 1;
    }

    installed = true;
    return 0;
}
