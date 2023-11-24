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

#include "der_scheduler_internal.h"
#include <libiec61850/hal_thread.h>

#include "cJSON.h"

#include <stdio.h> //TODO remove - only for debugging

struct sSchedulerStorage {
    char* filename; /* file name of the persistent database */
    cJSON* inMemoryDb; /* in-memory JSON database */
    Semaphore inMemoryDbLock; /* in-memory JSON database access control */
};

static char*
readFileToBuffer(const char* filename)
{
    char* buffer = NULL;

    FILE* file = fopen(filename, "r");

    if (file) {
        if (fseek(file, 0L, SEEK_END) == 0) {

            long fileSize = ftell(file);

            buffer = calloc(1, fileSize + 1);

            if (fseek(file, 0L, SEEK_SET) != 0) {
                printf("ERROR: cannot seek to start of file\n");
                free(buffer);
                return NULL;
            }

            size_t readBytes = fread(buffer, 1, fileSize, file);

            if (readBytes != fileSize) {
                printf("ERROR: failed to read file\n");
                free(buffer);
                return NULL;
            }

            buffer[readBytes] = 0; /* add string terminator */
        }

        fclose(file);
    }

    return buffer;
}

static bool
writeStringToFile(const char* filename, const char* str)
{
    FILE* file = fopen(filename, "w");

    if (file) {
        int ret = fprintf(file, "%s", str);

        fclose(file);

        if (ret < 0) {
            printf("ERROR: failed to write to file\n");

            return false;
        }
    }
    else {
        printf("ERROR: failed to open file for writing\n");

        return false;
    }

    return true;
}

static char*
convertObjRefToFilename(const char* objRef, char* buffer)
{
    /* convert "." to "_" and "/" to "_" */
    int pos = 0;
    int outPos = 0;

    while (objRef[pos] != 0) {
        if (objRef[pos] == '.') {
            buffer[outPos++] = '_';
        }
        else if (objRef[pos] == '/') {
            buffer[outPos++] = '_';
            buffer[outPos++] = '_';
        }
        else {
            buffer[outPos++] = objRef[pos];
        }

        pos++;
    }

    buffer[outPos++] = '.';
    buffer[outPos++] = 'j';
    buffer[outPos++] = 's';
    buffer[outPos++] = 'o';
    buffer[outPos++] = 'n';
    buffer[outPos++] = 0;
}

static char*
getFilenameForSchedule(Schedule schedule, char* buffer)
{
    char objRef[130];

    ModelNode_getObjectReferenceEx((ModelNode*)schedule->scheduleLn, objRef, false);

    return convertObjRefToFilename(objRef, buffer);
}

static char*
getFilenameForScheduleController(ScheduleController controller, char* buffer)
{
    char objRef[130];

    ModelNode_getObjectReferenceEx((ModelNode*)controller->controllerLn, objRef, false);

    return convertObjRefToFilename(objRef, buffer);
}

SchedulerStorage
SchedulerStorage_init(const char* databaseUri, int numberOfParameters, const char** parameters)
{
    SchedulerStorage self = (SchedulerStorage)calloc(1, sizeof(struct sSchedulerStorage));

    if (self) {
        self->filename = strdup(databaseUri);
        self->inMemoryDb = NULL;
        self->inMemoryDbLock = Semaphore_create(1);
    }

    return self;
}

void
SchedulerStorage_destroy(SchedulerStorage self)
{
    if (self) {
        free(self->filename);

        if (self->inMemoryDb)
            cJSON_Delete(self->inMemoryDb);

        if (self->inMemoryDbLock)
            Semaphore_destroy(self->inMemoryDbLock);

        free(self);
    }
}

static const char*
getStringFromState(ScheduleState state)
{
    switch(state) {
        case SCHD_STATE_NOT_READY:
            return "not ready";
        case SCHD_STATE_START_TIME_REQUIRED:
            return "start time required";
        case SCHD_STATE_READY:
            return "ready";
        case SCHD_STATE_RUNNING:
            return "running";
        case SCHD_STATE_INVALID:
        default:
            return "invalid";
    }
}

static ScheduleState
getStateFromString(const char* stateStr)
{
    if (!strcmp(stateStr, "not ready"))
        return SCHD_STATE_NOT_READY;
    if (!strcmp(stateStr, "start time required"))
        return SCHD_STATE_START_TIME_REQUIRED;
    if (!strcmp(stateStr, "ready"))
        return SCHD_STATE_READY;
    if (!strcmp(stateStr, "running"))
        return SCHD_STATE_RUNNING;
    else
        return SCHD_STATE_INVALID;
}

static cJSON*
createScheduleController(ScheduleController controller)
{
    cJSON* controllerJson = cJSON_CreateObject();

    if (controllerJson == NULL)
        goto exit_error;

    char controllerRef[130];
    ModelNode_getObjectReferenceEx((ModelNode*)controller->controllerLn, controllerRef, true);

    cJSON* objRef = cJSON_CreateString(controllerRef);
    cJSON_AddItemToObject(controllerJson, "objRef", objRef);

    /* CtlEnt.setSrcRef */
    DataAttribute* ctlEnt_setSrcRef = (DataAttribute*)ModelNode_getChild((ModelNode*)controller->controllerLn, "CtlEnt.setSrcRef");

    if (ctlEnt_setSrcRef) {
        cJSON* ctlEnt = cJSON_CreateString(MmsValue_toString(ctlEnt_setSrcRef->mmsValue));
        cJSON_AddItemToObject(controllerJson, "ctlEnt", ctlEnt);
    }

    /* save references */
    cJSON* schedules = cJSON_CreateArray();

    int refCount = ScheduleController_getRefCount(controller);

    for (int idx = 0; idx < refCount; idx++) {
        DataAttribute* refAttr = ScheduleController_getScheduleReferenceWithIdx(controller, idx);

        if (refAttr) {
            cJSON* schdRef = cJSON_CreateObject();
            
            cJSON* refId = cJSON_CreateString(refAttr->parent->name);
            cJSON_AddItemToObject(schdRef, "id", refId);

            cJSON* refSchd = cJSON_CreateString(MmsValue_toString(refAttr->mmsValue));
            cJSON_AddItemToObject(schdRef, "ref", refSchd);

            cJSON_AddItemToArray(schedules, schdRef);
        }
    }

    cJSON_AddItemToObject(controllerJson, "schedules", schedules);

exit:
    return controllerJson;

exit_error:
    if (controllerJson)
        cJSON_Delete(controllerJson);

    return NULL;
}

static void
addIntegerValue(cJSON* jsonObj, ModelNode* iedObj, const char* item)
{
    DataAttribute* da = (DataAttribute*)ModelNode_getChild(iedObj, item);

    if (da) {
        MmsValue* mmsValue = da->mmsValue;

        if (mmsValue && (MmsValue_getType(mmsValue) == MMS_INTEGER)) {
            int32_t val = MmsValue_toInt32(mmsValue);

            cJSON* jsonValue = cJSON_CreateNumber((double)val);

            cJSON_AddItemToObject(jsonObj, item, jsonValue);
        }
    }
}

static void
addUnsignedValue(cJSON* jsonObj, ModelNode* iedObj, const char* item)
{
    DataAttribute* da = (DataAttribute*)ModelNode_getChild(iedObj, item);

    if (da) {
        MmsValue* mmsValue = da->mmsValue;

        if (mmsValue && (MmsValue_getType(mmsValue) == MMS_UNSIGNED)) {
            uint32_t val = MmsValue_toUint32(mmsValue);

            cJSON* jsonValue = cJSON_CreateNumber((double)val);

            cJSON_AddItemToObject(jsonObj, item, jsonValue);
        }
    }
}

static cJSON*
createSchedule(Schedule schedule)
{
    cJSON* scheduleJson = cJSON_CreateObject();

    if (scheduleJson == NULL)
        goto exit_error;

    char schedRef[130];
    ModelNode_getObjectReferenceEx((ModelNode*)schedule->scheduleLn, schedRef, true);

    cJSON* objRef = cJSON_CreateString(schedRef);
    cJSON_AddItemToObject(scheduleJson, "objRef", objRef);

    cJSON* schdSt = cJSON_CreateString(getStringFromState(Schedule_getState(schedule)));
    cJSON_AddItemToObject(scheduleJson, "state", schdSt);

    cJSON* reuse = cJSON_CreateBool(Schedule_getSchdReuse(schedule));
    cJSON_AddItemToObject(scheduleJson, "reuse", reuse);

    cJSON* prio = cJSON_CreateNumber((double)Schedule_getPrio(schedule));
    cJSON_AddItemToObject(scheduleJson, "prio", prio);

    cJSON* numEntr = cJSON_CreateNumber((double)(Schedule_getNumEntr(schedule)));
    cJSON_AddItemToObject(scheduleJson, "numEntr", numEntr);

    cJSON* schdIntv = cJSON_CreateNumber((double)(Schedule_getSchdIntvInMs(schedule)));
    cJSON_AddItemToObject(scheduleJson, "schdIntv", schdIntv);

    cJSON* actStrTm = cJSON_CreateNumber((double)(schedule->startTime));
    cJSON_AddItemToObject(scheduleJson, "ActStrTm", actStrTm);

    /* save values */
    cJSON* values = cJSON_CreateArray();

    int valueCount = Schedule_getValueCount(schedule);

    for (int idx = 0; idx < valueCount; idx++) {
        MmsValue* mmsValue = Schedule_getValueWithIdx(schedule, idx);

        cJSON* value = NULL;

        if (MmsValue_getType(mmsValue) == MMS_BOOLEAN) {
            value = cJSON_CreateBool(MmsValue_getBoolean(mmsValue));
        }
        else if (MmsValue_getType(mmsValue) == MMS_FLOAT) {
            value = cJSON_CreateNumber((double)MmsValue_toFloat(mmsValue));
        }
        else if (MmsValue_getType(mmsValue) == MMS_INTEGER) {
            value = cJSON_CreateNumber((double)MmsValue_toInt32(mmsValue));
        }

        if (value) {
            cJSON_AddItemToArray(values, value);
        }
    }

    cJSON_AddItemToObject(scheduleJson, "values", values);

    cJSON* startTimes = cJSON_CreateArray();

    LogicalNode* schedLn = schedule->scheduleLn;

    DataObject* dobj = (DataObject*)(schedLn->firstChild);

    while (dobj) 
    {
        if (scheduler_checkIfMultiObjInst(dobj->name, "StrTm")) {

            uint64_t timeVal = 0L;

            DataAttribute* strTm_setTm = (DataAttribute*)ModelNode_getChild((ModelNode*)dobj, "setTm");

            if (strTm_setTm) {
                if (strTm_setTm->mmsValue && MmsValue_getType(strTm_setTm->mmsValue) == MMS_UTC_TIME) {
                    timeVal = MmsValue_getUtcTimeInMs(strTm_setTm->mmsValue);
                }
            }

            cJSON* strTm = cJSON_CreateObject();

            cJSON* startTimeId = cJSON_CreateString(dobj->name);

            cJSON_AddItemToObject(strTm, "id", startTimeId);

            cJSON* startTimeT = cJSON_CreateNumber((double)timeVal);

            cJSON_AddItemToObject(strTm, "t", startTimeT);

            DataAttribute* strTm_setCal = (DataAttribute*)ModelNode_getChild((ModelNode*)dobj, "setCal");

            if (strTm_setCal) {

                cJSON* setCal = cJSON_CreateObject();

                addUnsignedValue(setCal, (ModelNode*)strTm_setCal, "occ");
                addIntegerValue(setCal, (ModelNode*)strTm_setCal, "occType");
                addIntegerValue(setCal, (ModelNode*)strTm_setCal, "occPer");
                addIntegerValue(setCal, (ModelNode*)strTm_setCal, "weekDay");
                addIntegerValue(setCal, (ModelNode*)strTm_setCal, "month");
                addUnsignedValue(setCal, (ModelNode*)strTm_setCal, "day");
                addUnsignedValue(setCal, (ModelNode*)strTm_setCal, "hr");
                addUnsignedValue(setCal, (ModelNode*)strTm_setCal, "mn");

                cJSON_AddItemToObject(strTm, "setCal", setCal);
            }

            cJSON_AddItemToArray(startTimes, strTm);
        }    

        dobj = (DataObject*)(dobj->sibling);
    }

    cJSON_AddItemToObject(scheduleJson, "startTimes", startTimes);
    
    char* jsonStr = cJSON_PrintUnformatted(scheduleJson);

exit:
    return scheduleJson;

exit_error:
    if (scheduleJson)
        cJSON_Delete(scheduleJson);

    return NULL;
}

bool
SchedulerStorage_saveSchedule(SchedulerStorage self, Schedule schedule)
{
    char filename[200];
    getFilenameForSchedule(schedule, filename);

    cJSON* scheduleJson = createSchedule(schedule);

    if (scheduleJson) {
        char* jsonStr = cJSON_PrintUnformatted(scheduleJson);

        cJSON_Delete(scheduleJson);

        if (jsonStr) {
            writeStringToFile(filename, jsonStr);

            free(jsonStr);
        }
        else {
            printf("ERROR: failed to write schedule to file %s\n", filename);

            return false;
        }
    }
    else {
        printf("ERROR: Failed to convert schedule to json\n");

        return false;
    }

    return true;
}

bool
SchedulerStorage_saveScheduleController(SchedulerStorage self, ScheduleController controller)
{
    char filename[200];
    getFilenameForScheduleController(controller, filename);

    cJSON* controllerJson = createScheduleController(controller);

    if (controllerJson) {
        char* jsonStr = cJSON_PrintUnformatted(controllerJson);

        cJSON_Delete(controllerJson);

        if (jsonStr) {
            writeStringToFile(filename, jsonStr);

            free(jsonStr);
        }
        else {
            printf("ERROR: failed to write schedule controller to file %s\n", filename);

            return false;
        }
    }
    else {
        printf("ERROR: Failed to convert schedule controller to json\n");

        return false;
    }

    return true;
}

static bool
getScheduleControllerData(SchedulerStorage self, ScheduleController controller, const char* controllerJsonStr)
{
    char controllerRef[130];

    ModelNode_getObjectReferenceEx((ModelNode*)controller->controllerLn, controllerRef, true);

    cJSON* json = cJSON_Parse(controllerJsonStr);

    if (json == NULL)
    {
        printf("JSON-DB(ERROR): Failed to parse schedule controller\n");

        return false;
    }

    cJSON* objRef = cJSON_GetObjectItem(json, "objRef");

    if (objRef == NULL) {
        printf("JSON-DB(ERROR): No objRef in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsString(objRef) == false) {
        printf("JSON-DB(ERROR): objRef has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    cJSON* ctlEnt = cJSON_GetObjectItem(json, "ctlEnt");

    if (ctlEnt == NULL) {
        printf("JSON-DB(ERROR): No ctlEnt in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsString(ctlEnt) == false) {
        printf("JSON-DB(ERROR): ctlEnt has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    ScheduleController_setCtlEnt(controller, ctlEnt->valuestring);

    cJSON* schedules = cJSON_GetObjectItem(json, "schedules");

    if (schedules == NULL) {
        printf("JSON-DB(ERROR): No schedules in schedule controller\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsArray(schedules) == false) {
        printf("JSON-DB(ERROR): No schedules is not an array\n");

        cJSON_Delete(json);

        return false;
    }

    int valueCount = cJSON_GetArraySize(schedules);

    for (int i = 0; i < valueCount; i++) {
        cJSON* schedule = cJSON_GetArrayItem(schedules, i);
    
        if (schedule) {
            cJSON* scheduleId = cJSON_GetObjectItem(schedule, "id");

            cJSON* scheduleRef = cJSON_GetObjectItem(schedule, "ref");

            if (scheduleId && scheduleRef) {
                if (cJSON_IsString(scheduleId) && cJSON_IsString(scheduleRef)) {
                    ScheduleController_setSchdRef(controller, scheduleId->valuestring, scheduleRef->valuestring);
                }
                else {
                    printf("JSON-DB(ERROR): Schedule id or ref of wrong type\n");
                }
            }
            else {
                printf("JSON-DB(ERROR): Schedule is missing id or ref\n");
            }
        }
    }

    return true;
}

static bool
getScheduleData(SchedulerStorage self, Schedule schedule, const char* scheduleJsonStr)
{
    char schedRef[130];

    ModelNode_getObjectReferenceEx((ModelNode*)schedule->scheduleLn, schedRef, true);

    cJSON* json = cJSON_Parse(scheduleJsonStr);

    if (json == NULL)
    {
        printf("JSON-DB(ERROR): Failed to parse schedule\n");

        return false;
    }

    cJSON* objRef = cJSON_GetObjectItem(json, "objRef");

    if (objRef == NULL) {
        printf("JSON-DB(ERROR): No objRef in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsString(objRef) == false) {
        printf("JSON-DB(ERROR): objRef has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    cJSON* state = cJSON_GetObjectItem(json, "state");

    if (state == NULL) {
        printf("JSON-DB(ERROR): No state in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsString(state) == false) {
        printf("JSON-DB(ERROR): state has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    cJSON* reuse = cJSON_GetObjectItem(json, "reuse");

    if (reuse == NULL) {
        printf("JSON-DB(ERROR): No reuse in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsBool(reuse) == false) {
        printf("JSON-DB(ERROR): reuse has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    Schedule_setSchdReuse(schedule, (bool)reuse->valueint);
    
    cJSON* prio = cJSON_GetObjectItem(json, "prio");

    if (prio == NULL) {
        printf("JSON-DB(ERROR): No prio in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsNumber(prio) == false) {
        printf("JSON-DB(ERROR): prio has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    Schedule_setPrio(schedule, prio->valueint);

    cJSON* numEntr = cJSON_GetObjectItem(json, "numEntr");

    if (numEntr == NULL) {
        printf("JSON-DB(ERROR): No numEntr in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsNumber(numEntr) == false) {
        printf("JSON-DB(ERROR): numEntr has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    Schedule_setNumEntr(schedule, numEntr->valueint);

    schedule->numberOfScheduleEntries = numEntr->valueint;

    cJSON* schdIntv = cJSON_GetObjectItem(json, "schdIntv");

    if (schdIntv == NULL) {
        printf("JSON-DB(ERROR): No schdIntv in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsNumber(schdIntv) == false) {
        printf("JSON-DB(ERROR): schdIntv has invalid type\n");

        cJSON_Delete(json);

        return false;
    }

    Schedule_setSchIntvInMs(schedule, schdIntv->valueint);

    cJSON* actStrTm = cJSON_GetObjectItem(json, "ActStrTm");

    if (actStrTm) {
        if (cJSON_IsNumber(actStrTm) == false) {
            printf("JSON-DB(ERROR): ActStrTm has invalid type\n");
        }
        else {
            Schedule_updateActStrTm(schedule, (uint64_t)(actStrTm->valuedouble));
        }
    }

    cJSON* values = cJSON_GetObjectItem(json, "values");

    if (values == NULL) {
        printf("JSON-DB(ERROR): No values in schedule\n");

        cJSON_Delete(json);

        return false;
    }

    if (cJSON_IsArray(values) == false) {
        printf("JSON-DB(ERROR): No values is not an array\n");

        cJSON_Delete(json);

        return false;
    }

    int valueCount = cJSON_GetArraySize(values);

    for (int i = 0; i < valueCount; i++) {
        cJSON* value = cJSON_GetArrayItem(values, i);
       
        MmsValue* mmsValue = Schedule_getValueWithIdx(schedule, i);

        if (mmsValue) {
            if (MmsValue_getType(mmsValue) == MMS_BOOLEAN) {
                MmsValue_setBoolean(mmsValue, value->valueint);
            }
            else if (MmsValue_getType(mmsValue) == MMS_FLOAT) {
                MmsValue_setFloat(mmsValue, (float)value->valuedouble);
            }
            else if (MmsValue_getType(mmsValue) == MMS_INTEGER) {
                MmsValue_setInt32(mmsValue, value->valueint);
            }
        }
        else {
            printf("ERROR:   value with index %i not found in schedule!\n", i);
        }
    }

    cJSON* startTimes = cJSON_GetObjectItem(json, "startTimes");

    if (startTimes) {
        int startTimeCount = cJSON_GetArraySize(startTimes);

        for (int i = 0; i < startTimeCount; i++) {
            cJSON* startTime = cJSON_GetArrayItem(startTimes, i);

            if (startTime) {
                cJSON* id = cJSON_GetObjectItem(startTime, "id");

                if (id) {
                    DataAttribute* strTm = (DataAttribute*)ModelNode_getChild((ModelNode*)schedule->scheduleLn, id->valuestring);

                    if (strTm) {
                        DataAttribute* strTm_setTm = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm, "setTm");

                        if (strTm_setTm) {

                            if (strTm_setTm->mmsValue && MmsValue_getType(strTm_setTm->mmsValue) == MMS_UTC_TIME) {

                                cJSON* t = cJSON_GetObjectItem(startTime, "t");

                                uint64_t tValue = t ? (uint64_t)t->valuedouble : (uint64_t)0;

                                MmsValue_setUtcTimeMs(strTm_setTm->mmsValue, tValue);
                            }
                        }
                        else {
                            printf("INFO: Start time %s has no setTm attribute\n", id->valuestring);
                        }

                        DataAttribute* strTm_setCal = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm, "setCal");

                        if (strTm_setCal) {

                            cJSON* setCal = cJSON_GetObjectItem(startTime, "setCal");

                            if (setCal) {

                                cJSON* setCal_occ = cJSON_GetObjectItem(setCal, "occ");
                                DataAttribute* strTm_setCal_occ = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "occ");

                                if (setCal_occ && strTm_setCal_occ) {
                                    MmsValue_setUint32(strTm_setCal_occ->mmsValue, setCal_occ->valueint);
                                }

                                cJSON* setCal_occType = cJSON_GetObjectItem(setCal, "occType");
                                DataAttribute* strTm_setCal_occType = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "occType");

                                if (setCal_occType && strTm_setCal_occType) {
                                    MmsValue_setInt32(strTm_setCal_occType->mmsValue, setCal_occType->valueint);
                                }

                                cJSON* setCal_occPer = cJSON_GetObjectItem(setCal, "occPer");
                                DataAttribute* strTm_setCal_occPer = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "occPer");

                                if (setCal_occPer && strTm_setCal_occPer) {
                                    MmsValue_setInt32(strTm_setCal_occPer->mmsValue, setCal_occPer->valueint);
                                }

                                cJSON* setCal_weekDay = cJSON_GetObjectItem(setCal, "weekDay");
                                DataAttribute* strTm_setCal_weekDay = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "weekDay");

                                if (setCal_weekDay && strTm_setCal_weekDay) {
                                    MmsValue_setInt32(strTm_setCal_weekDay->mmsValue, setCal_weekDay->valueint);
                                }

                                cJSON* setCal_month = cJSON_GetObjectItem(setCal, "month");
                                DataAttribute* strTm_setCal_month = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "month");

                                if (setCal_month && strTm_setCal_month) {
                                    MmsValue_setInt32(strTm_setCal_month->mmsValue, setCal_month->valueint);
                                }

                                cJSON* setCal_day = cJSON_GetObjectItem(setCal, "day");
                                DataAttribute* strTm_setCal_day = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "day");

                                if (setCal_day && strTm_setCal_day) {
                                    MmsValue_setUint32(strTm_setCal_day->mmsValue, setCal_day->valueint);
                                }

                                cJSON* setCal_hr = cJSON_GetObjectItem(setCal, "hr");
                                DataAttribute* strTm_setCal_hr = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "hr");

                                if (setCal_hr && strTm_setCal_hr) {
                                    MmsValue_setUint32(strTm_setCal_hr->mmsValue, setCal_hr->valueint);
                                }

                                cJSON* setCal_mn = cJSON_GetObjectItem(setCal, "mn");
                                DataAttribute* strTm_setCal_mn = (DataAttribute*)ModelNode_getChild((ModelNode*)strTm_setCal, "mn");

                                if (setCal_mn && strTm_setCal_mn) {
                                    MmsValue_setUint32(strTm_setCal_mn->mmsValue, setCal_mn->valueint);
                                }
                            }

                        }
                    }
                    else {
                        printf("ERROR: Start time %s not found\n", id->valuestring);
                    }
                }
                else {
                    printf("     ERROR: start time %i has no id\n", i);
                }
            }
        }
    }

    /* update schedule state as the last step to ensure that all schedule data is already available
       when schedule is about to run */
    ScheduleState schedState = getStateFromString(state->valuestring);

    Schedule_setState(schedule, schedState);

    cJSON_Delete(json);

    return true;
}

bool
SchedulerStorage_restoreSchedule(SchedulerStorage self, Schedule schedule)
{
    cJSON* scheduleJson = NULL;

    char filename[200];
    getFilenameForSchedule(schedule, filename);

    char* buffer = readFileToBuffer(filename);

    if (buffer == NULL) {
        scheduleJson = createSchedule(schedule);

        char* jsonStr = cJSON_PrintUnformatted(scheduleJson);

        if (jsonStr) {
            writeStringToFile(filename, jsonStr);

            free(jsonStr);
        }
        else {
            printf("ERROR: failed to write schedule to file %s\n", filename);
        }
    }
    else {
        scheduleJson = cJSON_Parse(buffer);
        free(buffer);
    }

    if (scheduleJson) {
        char* jsonStr = cJSON_Print(scheduleJson);

        getScheduleData(self, schedule, jsonStr);

        cJSON_Delete(scheduleJson);
    }

    return true;
}

bool
SchedulerStorage_restoreScheduleController(SchedulerStorage self, ScheduleController controller)
{
    cJSON* scheduleJson = NULL;

    char filename[200];
    getFilenameForScheduleController(controller, filename);

    char* buffer = readFileToBuffer(filename);

    if (buffer == NULL) {
        scheduleJson = createScheduleController(controller);

        char* jsonStr = cJSON_PrintUnformatted(scheduleJson);

        if (jsonStr) {
            writeStringToFile(filename, jsonStr);

            free(jsonStr);
        }
        else {
            printf("ERROR: failed to write schedule controller to file %s\n", filename);
        }
    }
    else {
        scheduleJson = cJSON_Parse(buffer);
        free(buffer);
    }

    if (scheduleJson) {
        char* jsonStr = cJSON_Print(scheduleJson);

        getScheduleControllerData(self, controller, jsonStr);

        cJSON_Delete(scheduleJson);
    }

    return true;
}
