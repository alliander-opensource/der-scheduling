#ifndef _DER_SCHEDULER_INTERNAL_H
#define _DER_SCHEDULER_INTERNAL_H

#include "der_scheduler.h"

#include <libiec61850/hal_thread.h>

typedef struct sSchedule* Schedule;

typedef struct sScheduleController* ScheduleController;

typedef struct sSchedulerStorage* SchedulerStorage;

typedef enum {
    SCHD_STATE_INVALID = 0,
    SCHD_STATE_NOT_READY = 1,
    SCHD_STATE_START_TIME_REQUIRED = 2,
    SCHD_STATE_READY = 3,
    SCHD_STATE_RUNNING = 4
} ScheduleState;

typedef enum {
    SCHD_ENA_ERR_NONE = 1,
    SCHD_ENA_ERR_MISSING_VALID_NUMENTR = 2,
    SCHD_ENA_ERR_MISSING_VALID_SCHDINTV = 3,
    SCHD_ENA_ERR_MISSING_VALID_SCHEDULE_VALUES = 4,
    SCHD_ENA_ERR_UNCONSISTENT_VALUES_CDC = 5,
    SCHD_ENA_ERR_MISSING_VALID_STRTM = 6,
    SCHD_ENA_ERR_OTHER = 99
} ScheduleEnablingError;

typedef enum {
    SCHD_TYPE_UNKNOWN = 0,
    SCHD_TYPE_INS = 1,
    SCHD_TYPE_SPS = 2,
    SCHD_TYPE_ENS = 3,
    SCHD_TYPE_MV = 4
} ScheduleTargetType;

struct sSchedule {
    LogicalNode* scheduleLn;
    ScheduleTargetType targetType;
    SchedulerStorage storage;

    DataObject* enaReq;
    DataObject* dsaReq;
    DataObject* schdSt;
    DataObject* val;

    DataObject* evTrg;

    uint64_t nextStartTime;

    bool reuse; /* allows to reuse the schedule after execution */

    uint64_t startTime; /* start time of current schedule execution */
    int entryDurationInMs; /* duration of schedule entry in ms */
    int numberOfScheduleEntries; /* number of valid schedule entries */
    int currentEntryIdx;

    IedServer server;
    IedModel* model;

    Thread thread;
    bool alive;

    bool allowRemoteControl; /* allow remote control of EnaReq/DsaReq */
    bool allowWriteToSchdPrio;
    bool allowWriteToStrTm;
    bool allowWriteToSchdReuse;
    bool allowWriteToSchdIntv;

    bool isTimeTriggerd; /* when the schedule has at least one StrTm object */
    bool isPeriodic;     /* when the schedule has at least one StrTm object with a setCal attribute */

    LinkedList knownScheduleControllers; /* list of ScheduleControllers to inform on state/value change events */
};

struct sScheduleController {
    Schedule activeSchedule;
    LinkedList schedules;
    SchedulerStorage storage;

    LogicalNode* controllerLn;
    IedServer server;
    IedModel* model;
    Scheduler scheduler;

    ModelNode* controlEntity; /* target object to be controlled by the schedule controller */
};

struct sScheduler
{
    IedModel* model;
    IedServer server;
    LinkedList scheduleController;
    LinkedList schedules;
    SchedulerStorage storage;

    Scheduler_TargetValueChanged targetValueHandler;
    void* targetValueHandlerParameter;
};

void
scheduler_targetValueChanged(Scheduler self, ModelNode* targetAttr, MmsValue* value, Quality quality, uint64_t timestampMs);

ScheduleController
ScheduleController_create(LogicalNode* fsccLn, Scheduler scheduler);

void
ScheduleController_destroy(ScheduleController self);

int
ScheduleController_getRefCount(ScheduleController self);

DataAttribute*
ScheduleController_getScheduleReferenceWithIdx(ScheduleController self, int idx);

void
ScheduleController_setCtlEnt(ScheduleController self, const char* ctlEntValue);

bool
ScheduleController_setSchdRef(ScheduleController self, const char* id, const char* ref);

void
scheduleController_schedulePrioUpdated(ScheduleController self, Schedule sched, int newPrio);

void
scheduleController_scheduleStateUpdated(ScheduleController self, Schedule sched, ScheduleState newState);

void
scheduleController_scheduleValueUpdated(ScheduleController self, Schedule sched, MmsValue* val, uint64_t timestamp);

void
ScheduleController_initialize(ScheduleController self);

Schedule
Schedule_create(LogicalNode* schedLn, IedServer server, IedModel* model);

int
Schedule_getPrio(Schedule self);

void
Schedule_setPrio(Schedule self, int value);

bool
Schedule_getSchdReuse(Schedule self);

void
Schedule_setSchdReuse(Schedule self, bool reuse);

int
Schedule_getSchdIntvInMs(Schedule self);

void
Schedule_setSchIntvInMs(Schedule self, int value);

int
Schedule_getNumEntr(Schedule self);

void
Schedule_setNumEntr(Schedule self, int numEntry);

int
Schedule_getValueCount(Schedule self);

bool
Schedule_isRunning(Schedule self);

MmsValue*
Schedule_getCurrentValue(Schedule self);

MmsValue*
Schedule_getValueWithIdx(Schedule self, int idx);

ScheduleState
Schedule_getState(Schedule self);

void
Schedule_setState(Schedule self, ScheduleState state);

void
Schedule_destroy(Schedule self);

bool 
scheduler_checkIfMultiObjInst(const char* name, const char* multiName);

Schedule
Scheduler_getScheduleByObjRef(Scheduler self, const char* objRef);

void
Schedule_setListeningController(Schedule self, ScheduleController controller);

void
Schedule_enableScheduleControl(Schedule self, bool enable);

bool
Schedule_enableSchedule(Schedule self, bool enable);

void
Schedule_enableWriteAccessToSchdPrio(Schedule self, bool enable);

void
Schedule_enableWriteAccessToStrTm(Schedule self, bool enable);

void
Schedule_enableWriteAccessToSchdReuse(Schedule self, bool enable);

SchedulerStorage
SchedulerStorage_init(const char* databaseUri, int numberOfParameters, const char** parameters);

void
SchedulerStorage_destroy(SchedulerStorage self);

bool
SchedulerStorage_saveSchedule(SchedulerStorage self, Schedule schedule);

bool
SchedulerStorage_restoreSchedule(SchedulerStorage self, Schedule schedule);

bool
SchedulerStorage_saveScheduleController(SchedulerStorage self, ScheduleController controller);

bool
SchedulerStorage_restoreScheduleController(SchedulerStorage self, ScheduleController controller);


#endif /* _DER_SCHEDULER_INTERNAL_H */
