"""Build a deterministic harness around the actual C teardown function bodies.
Compile emitted C for Android and run on the headset; no media or input is sent.
The mock workers assert join/clock ordering, including naturally exited threads.
"""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
lib=root/'third_party/RPiPlay/lib'
def function(file,name):
    text=(lib/file).read_text()
    match=re.search(r'(?:static\s+)?void\s+'+name+r'\([^)]*\)\s*\{',text)
    begin=match.start(); end=match.end(); depth=1
    while depth:
        if text[end]=='{': depth+=1
        elif text[end]=='}': depth-=1
        end+=1
    return text[begin:end]

prefix=r'''
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
static int clock_alive=1, joins[4], closes, ended;
#define MUTEX_LOCK(x) ((void)0)
#define MUTEX_UNLOCK(x) ((void)0)
#define COND_SIGNAL(x) ((void)0)
#define LOGGER_DEBUG 0
#define LOGGER_INFO 0
#define logger_log(...) ((void)0)
#define THREAD_JOIN(x) do { assert(clock_alive); joins[x]++; } while(0)
static void closesocket(int fd) { assert(joins[fd]); closes++; }
typedef struct {int running,joined,thread,thread_mirror,mirror_data_sock,csock,dsock,tsock;void *buffer;} worker;
typedef worker raop_rtp_t;
typedef worker raop_rtp_mirror_t;
typedef worker raop_ntp_t;
static void raop_buffer_flush(void *b,int i) {}
'''
stops='\n'.join(function(f,n) for f,n in [('raop_rtp.c','raop_rtp_stop'),('raop_rtp_mirror.c','raop_rtp_mirror_stop'),('raop_ntp.c','raop_ntp_stop')])
middle=r'''
static void raop_rtp_destroy(worker *w) {raop_rtp_stop(w);assert(w->joined);}
static void raop_rtp_mirror_destroy(worker *w) {raop_rtp_mirror_stop(w);assert(w->joined);}
static void raop_ntp_destroy(worker *w) {assert(joins[1] && joins[2]);raop_ntp_stop(w);assert(w->joined);clock_alive=0;}
static void pairing_session_destroy(void *p) {}
static void fairplay_destroy(void *p) {}
typedef struct {void *cls;void (*conn_destroy)(void*);void (*video_session_end)(void*,worker*);void (*video_flush)(void*);} callbacks;
typedef struct {void *logger;callbacks callbacks;} raop;
typedef struct {raop *raop;worker *raop_ntp,*raop_rtp,*raop_rtp_mirror;void *local,*remote,*pairing,*fairplay;} raop_conn_t;
static void ended_video(void *p,worker *ntp){assert(clock_alive && joins[1] && joins[2]);ended++;}
static void ended_conn(void *p){assert(!clock_alive);}
static void flush(void *p){}
'''
main=r'''
int main(void) {
    // An exited worker still owns its final stack/locks until join completes.
    for(int running=0;running<=1;running++) {
        joins[1]=joins[2]=joins[3]=0;clock_alive=1;ended=closes=0;
        worker audio={.running=running,.thread=1,.csock=1,.dsock=-1};
        worker video={.running=running,.thread_mirror=2,.mirror_data_sock=2};
        worker ntp={.running=running,.thread=3,.tsock=3};
        raop server={.callbacks={.video_session_end=ended_video,.conn_destroy=ended_conn,.video_flush=flush}};
        raop_conn_t *conn=calloc(1,sizeof(*conn));
        conn->raop=&server;conn->raop_rtp=&audio;conn->raop_rtp_mirror=&video;conn->raop_ntp=&ntp;
        conn_destroy(conn);
        assert(joins[1]==1 && joins[2]==1 && joins[3]==1 && ended==1 && closes==3);
        // Stop must be idempotent after the join, even though the clock is gone.
        raop_rtp_stop(&audio);raop_rtp_mirror_stop(&video);raop_ntp_stop(&ntp);
        assert(joins[1]==1 && joins[2]==1 && joins[3]==1);
    }
    puts("PASS: running and naturally exited workers join before clock destruction; session-end follows producers; repeated stop is inert");
}
'''
path=root/'verification/native_lifecycle_test.generated.c'
path.write_text(prefix+stops+middle+function('raop.c','conn_destroy')+main)
print(path)
