/*
 * Force-included (-include) into the native sources under test (logpath.c, rawdiag.c) and included by
 * harness.c before ring.c. It routes their mutex calls through harness_hooks.c, which records which mutexes
 * each thread holds, and the native path's ring pushes through a journalling wrapper in harness.c.
 */
#ifndef PARKCAST_HARNESS_WRAP_H
#define PARKCAST_HARNESS_WRAP_H

#include <pthread.h>
#include <stdint.h>

int harness_mutex_lock(pthread_mutex_t *m);
int harness_mutex_unlock(pthread_mutex_t *m);
#define pthread_mutex_lock harness_mutex_lock
#define pthread_mutex_unlock harness_mutex_unlock

#ifndef HARNESS_NO_RING_WRAP
int harness_ring_push(int gen, int kind, int a, int b, int64_t pts_ms, const char *text,
                      const unsigned char *data, int len);
#define ring_push harness_ring_push
#endif

#endif
