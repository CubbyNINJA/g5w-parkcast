/*
 * Mutex tracking for the native test harness: compiled without harness_wrap.h, so these call the real
 * pthread functions. Each thread keeps the set of tracked mutexes it holds.
 */
#include <pthread.h>

#define HELD_MAX 16

static __thread void *t_held[HELD_MAX];
static __thread int t_nheld;

int harness_mutex_lock(pthread_mutex_t *m)
{
    int r = pthread_mutex_lock(m);
    if (r == 0 && t_nheld < HELD_MAX) t_held[t_nheld++] = m;
    return r;
}

int harness_mutex_unlock(pthread_mutex_t *m)
{
    for (int i = t_nheld - 1; i >= 0; i--) {
        if (t_held[i] == m) {
            t_held[i] = t_held[--t_nheld];
            break;
        }
    }
    return pthread_mutex_unlock(m);
}

/* 1 when the calling thread holds exactly one tracked mutex, and it is m. */
int harness_held_only(void *m) { return t_nheld == 1 && t_held[0] == m; }
int harness_held_count(void) { return t_nheld; }
