#include "der_scheduler.h"

#include <signal.h>
#include <stdio.h>
#include <stdlib.h>

#include <libiec61850/hal_thread.h>

static bool running = false;

static Semaphore outputQueueLock = NULL;
static LinkedList outputQueue = NULL;

struct sOutputData {
    char* targetObjRef;
    char* targetValue;
};

typedef struct sOutputData* OutputData;

static void
sigint_handler(int signalId)
{
    running = false;
}

static void
scheduler_TargetValueChanged(void* parameter, const char* targetValueObjRef, MmsValue* value, Quality quality, uint64_t timestampMs)
{
    char mmsValueBuf[200];
    if (value) {
        MmsValue_printToBuffer(value, mmsValueBuf, 200);

        printf("Received target value change: %s: %s\n", targetValueObjRef, mmsValueBuf);

        OutputData outputData = malloc(sizeof(struct sOutputData));

        if (outputData) {
            outputData->targetObjRef = strdup(targetValueObjRef);
            outputData->targetValue = strdup(mmsValueBuf);

            Semaphore_wait(outputQueueLock);

            LinkedList_add(outputQueue, outputData);

            Semaphore_post(outputQueueLock);
        }
    }
}

static void*
outputWorkerThread(void* parameter)
{
    while (running) 
    {
        bool isEmpty = true;

        OutputData outputData = NULL;

        Semaphore_wait(outputQueueLock);

        LinkedList firstElement = LinkedList_get(outputQueue, 0);

        if (firstElement) {
            outputData = LinkedList_getData(firstElement);

            LinkedList_remove(outputQueue, outputData);

            firstElement = LinkedList_get(outputQueue, 0);

            if (firstElement)
                isEmpty = false;
        }

        Semaphore_post(outputQueueLock);

        if (outputData) {
            printf("Target value changed: %s: %s\n", outputData->targetObjRef, outputData->targetValue);

            /* call bash script for demo */
            char cmdBuf[500];
            snprintf(cmdBuf, 499, "./set_output.sh %s %s", outputData->targetObjRef, outputData->targetValue);

            system(cmdBuf);

            free(outputData->targetObjRef);
            free(outputData->targetValue);
            free(outputData);
        }

        if (isEmpty) 
            Thread_sleep(50);
    }
}

int
main(int argc, char** argv)
{
    outputQueue = LinkedList_create();
    outputQueueLock = Semaphore_create(1);

    signal(SIGINT, sigint_handler);

    IedModel* model = ConfigFileParser_createModelFromConfigFileEx("model.cfg");

    if (model) {
        IedServer server = IedServer_create(model);

        Scheduler sched = Scheduler_create(model, server);

        Scheduler_setTargetValueHandler(sched, scheduler_TargetValueChanged, server);

        /* configure fallback schedules */

        /* don't allow remote control of fallback schedules */
        Scheduler_enableScheduleControl(sched, "@Control/ActPow_Res_FSCH01", false);
        Scheduler_enableScheduleControl(sched, "@Control/MaxPow_Res_FSCH01", false);
        Scheduler_enableScheduleControl(sched, "@Control/OnOff_Res_FSCH01", false);

        /* block remote change of schedule parameters */
        Scheduler_enableWriteAccessToParameter(sched, "@Control/ActPow_Res_FSCH01", SCHED_PARAM_STR_TM, false);
        Scheduler_enableWriteAccessToParameter(sched, "@Control/ActPow_Res_FSCH01", SCHED_PARAM_SCHD_PRIO, false);

        Scheduler_enableWriteAccessToParameter(sched, "@Control/MaxPow_Res_FSCH01", SCHED_PARAM_STR_TM, false);
        Scheduler_enableWriteAccessToParameter(sched, "@Control/MaxPow_Res_FSCH01", SCHED_PARAM_SCHD_PRIO, false);

        Scheduler_enableWriteAccessToParameter(sched, "@Control/OnOff_Res_FSCH01", SCHED_PARAM_STR_TM, false);
        Scheduler_enableWriteAccessToParameter(sched, "@Control/OnOff_Res_FSCH01", SCHED_PARAM_SCHD_PRIO, false);

        /* enable all fallback schedules */
        Scheduler_enableSchedule(sched, "@Control/ActPow_Res_FSCH01", true);
        Scheduler_enableSchedule(sched, "@Control/MaxPow_Res_FSCH01", true);
        Scheduler_enableSchedule(sched, "@Control/OnOff_Res_FSCH01", true);

        Scheduler_initializeStorage(sched, "scheduler-db.json", 0, NULL);

        Thread workerThread = Thread_create(outputWorkerThread, NULL, false);

        IedServer_start(server, 102);

        if (IedServer_isRunning(server)) {

            running = true;

            Thread_start(workerThread);

            while (running) {
                Thread_sleep(100);
            }            
        }
        else {
            printf("ERROR: Cannot start server\n");
        }

        Scheduler_destroy(sched);
        IedServer_destroy(server);
        IedModel_destroy(model);

        Thread_destroy(workerThread);
    }
    else {
        printf("ERROR: Failed to load data model\n");
    }

    LinkedList_destroy(outputQueue);
    Semaphore_destroy(outputQueueLock);
}