/*
 * Copyright 2023 MZ Automation GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

#ifndef _DER_SCHEDULER_H
#define _DER_SCHEDULER_H

#include <libiec61850/iec61850_server.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct sScheduler* Scheduler;

/**
 * @brief Create a new Scheduler instance
 * 
 * @param model the data model containing schedule controller and schedule logical nodes
 * @param server the server to be attached
 * @return Scheduler 
 */
Scheduler
Scheduler_create(IedModel* model, IedServer server);

/**
 * @brief Set a storage instance to use to persist schedule data
 * 
 * @param self the scheduler instance
 * @param databaseUri data base URI that are passed to the persistency layer
 * @param numberOfParameter number of parameters that are passed to the persitency layer
 * @param parameters parameters that are passed to the persistency layer
*/
void
Scheduler_initializeStorage(Scheduler self, const char* databaseUri, int numberOfParameters, const char** parameters);

/**
 * @brief Callback to receive notifications on target value changes
 * 
 * @param parameter user provided parameter that is passed to the callback
 * @param targetValueObjRef object reference of the target value (LDinst/LN.DO...)
 * @param value the updated value of the target
 * @param quality the quality of the target value
 * @param timestampMs the timestamp of the target value (in ms since epoch)
 */
typedef void
(*Scheduler_TargetValueChanged)(void* parameter, const char* targetValueObjRef, MmsValue* value, Quality quality, uint64_t timestampMs);

/**
 * @brief Set the callback handler for target value changes
 * 
 * @param self the scheduler instance
 * @param handler callback handler
 * @param parameter user provided parameter to be passed to the callback handler
 */
void
Scheduler_setTargetValueHandler(Scheduler self, Scheduler_TargetValueChanged handler, void* parameter);

/**
 * @brief Get the current target value of a schedule controller
 * 
 * @param self the scheduler instance
 * @param controllerRef object reference of the schedule controller (LDInst/LN)
 * @param targetRef user provided buffer to write the current target reference (at least 130 characters)
 * @return MmsValue* current target value or NULL if no target value exists
 */
MmsValue*
Scheduler_getTargetValue(Scheduler self, const char* controllerRef, char* targetRef);

/**
 * @brief Enable or disable remote client control of a schedule with EnaReq/DsaReq
 * 
 * When remote client control is disabled the server will deny control commands send
 * to EnaReq or DsaReq.
 * 
 * @param self the scheduler instance
 * @param scheduleRef the object reference of the Schedule (@LDInst/LN)
 * @param enable true to enable remote control, or false to disable remote control
 */
void
Scheduler_enableScheduleControl(Scheduler self, const char* scheduleRef, bool enable);

LinkedList /* <ScheduleEvent> */
Scheduler_createScheduleForecast(Scheduler self, const char* scheduleRef, uint64_t startTime, uint64_t endTime);

/**
 * @brief Create a schedule forecast for the specified schedule controller and time period
 *
 * @param self the scheduler instance
 * @param schedCtrRef the object reference of the ScheduleController (@LDInst/LN)
 * @param startTime the start time of the forecast time period (in ms since Epoch)
 * @param endTime the end time of the forecast time period (in ms since Epoch)
 *
 * @return the list of schedule events (value changes) during the time period
 */
LinkedList /* <ScheduleEvent> */
Scheduler_createForecast(Scheduler self, const char* schedCtrRef, uint64_t startTime, uint64_t endTime);

const char*
Scheduler_getCtlEntityRef(Scheduler self, const char* schedCtrlRef);

/**
 * @brief Enable or disable a schedule
 * 
 * @param self the scheduler instance
 * @param scheduleRef the object reference of the Schedule (@LDInst/LN)
 * @param enable true to enable the schedule, or false to disable the schedule
 * 
 * @return true on success, false otherwise
 */
bool
Scheduler_enableSchedule(Scheduler self, const char* scheduleRef, bool enable);

typedef enum {
    SCHED_PARAM_SCHD_PRIO = 1,
    SCHED_PARAM_STR_TM,
    SCHED_PARAM_SCHD_REUSE
} Scheduler_ScheduleParameter;

/**
 * @brief Enable or disable remote client write access to specific parameters of a Schedule instance
 *  
 * @param self the scheduler instance
 * @param scheduleRef the object reference of the Schedule (@LDInst/LN)
 * @param parameter the parameter to be enabled or disabled
 * @param enable true to enable remote write access, false to disable
 */
void
Scheduler_enableWriteAccessToParameter(Scheduler self, const char* scheduleRef, Scheduler_ScheduleParameter parameter, bool enable);

/**
 * @brief Stop scheduler and release all resources
 * 
 * Do not use the reference after this call!
 * 
 * @param self the scheduler instance
 */
void
Scheduler_destroy(Scheduler self);

typedef struct sScheduleEvent* ScheduleEvent;

MmsValue*
ScheduleEvent_getValue(ScheduleEvent self);

uint64_t
ScheduleEvent_getTime(ScheduleEvent self);

void
ScheduleEvent_destroy(ScheduleEvent self);

#ifdef __cplusplus
}
#endif

#endif /* _DER_SCHEDULER_H */
