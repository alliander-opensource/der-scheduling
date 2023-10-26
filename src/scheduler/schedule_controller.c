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

#include <stdio.h>

static void
scheduleController_updateActSchdRef(ScheduleController self, Schedule schedule)
{
    DataObject* actSchdRef = (DataObject*)ModelNode_getChild((ModelNode*)self->controllerLn, "ActSchdRef");

    if (actSchdRef) {
        DataAttribute* actSchdRef_stVal = (DataAttribute*)ModelNode_getChild((ModelNode*)actSchdRef, "stVal");
        DataAttribute* actSchdRef_t = (DataAttribute*)ModelNode_getChild((ModelNode*)actSchdRef, "t");
        DataAttribute* actSchdRef_q = (DataAttribute*)ModelNode_getChild((ModelNode*)actSchdRef, "q");

        //IedServer_lockDataModel(self->server);

        if (schedule) {
            if (actSchdRef_stVal) {
                char objRefBuf[130];

                char* objRef = ModelNode_getObjectReference((ModelNode*)schedule->scheduleLn, objRefBuf);

                IedServer_updateVisibleStringAttributeValue(self->server, actSchdRef_stVal, objRef);
            }

            if (actSchdRef_q)
                IedServer_updateQuality(self->server, actSchdRef_q, QUALITY_VALIDITY_GOOD);
        }
        else {
            if (actSchdRef_stVal)
                IedServer_updateVisibleStringAttributeValue(self->server, actSchdRef_stVal, "");

            if (actSchdRef_q)
                IedServer_updateQuality(self->server, actSchdRef_q, QUALITY_VALIDITY_INVALID);
        }

        if (actSchdRef_t) {
            Timestamp ts;
            Timestamp_clearFlags(&ts);
            Timestamp_setTimeInMilliseconds(&ts, Hal_getTimeInMs());

            IedServer_updateTimestampAttributeValue(self->server, actSchdRef_t, &ts);
        }

        //IedServer_unlockDataModel(self->server);
    }
}

static Schedule
scheduleController_getActiveSchedule(ScheduleController self)
{
    Schedule activeSchedule = NULL;

    LinkedList schedElem = LinkedList_getNext(self->schedules);

    while (schedElem) {
        Schedule schedule = (Schedule)LinkedList_getData(schedElem);

        if (Schedule_isRunning(schedule)) {
            if (activeSchedule == NULL) {
                activeSchedule = schedule;
            }
            else {
                if (Schedule_getPrio(schedule) > Schedule_getPrio(activeSchedule)) {
                    activeSchedule = schedule;
                }
            }
        }

        schedElem = LinkedList_getNext(schedElem);
    }

    return activeSchedule;
}

static void
scheduleController_updateTargetValue(ScheduleController self, ScheduleTargetType targetType, MmsValue* val, uint64_t currentTime)
{
    if (self->controlEntity) {
        DataAttribute* valueAttr = NULL;
        DataAttribute* qAttr = NULL;
        DataAttribute* tAttr = NULL;

        if (self->controlEntity->modelType == DataObjectModelType) {

            if (targetType == SCHD_TYPE_MV) {
                ModelNode* mag_f = ModelNode_getChild(self->controlEntity, "mag.f");

                if (mag_f) {
                    valueAttr = (DataAttribute*)mag_f;
                }
                else {
                    ModelNode* mag_i = ModelNode_getChild(self->controlEntity, "mag.i");

                    valueAttr = (DataAttribute*)mag_i;
                }
                //TODO handle instMag?
            }
            else {
                ModelNode* stVal = ModelNode_getChild(self->controlEntity, "stVal");

                valueAttr = (DataAttribute*)stVal;
            }

            tAttr = (DataAttribute*)ModelNode_getChild(self->controlEntity, "t");
            qAttr = (DataAttribute*)ModelNode_getChild(self->controlEntity, "q");
        }
        else if (self->controlEntity->modelType == DataAttributeModelType) {
            valueAttr = (DataAttribute*)self->controlEntity;

            ModelNode* parent = ModelNode_getParent(self->controlEntity);

            if (parent) {
                if (parent->modelType != DataObjectModelType) {
                    parent = ModelNode_getParent(parent);
                }

                if (parent->modelType == DataObjectModelType) {
                    tAttr = (DataAttribute*)ModelNode_getChild(parent, "t");
                    qAttr = (DataAttribute*)ModelNode_getChild(parent, "q");
                }
            }
        }

        Quality q = QUALITY_VALIDITY_GOOD;

        if (val == NULL) {
            q = QUALITY_VALIDITY_INVALID;
        }

        if (tAttr) {
            IedServer_updateUTCTimeAttributeValue(self->server, tAttr, currentTime);
        }

        if (qAttr) {
            IedServer_updateQuality(self->server, qAttr, q);
        }

        if (val && valueAttr) {
            IedServer_updateAttributeValue(self->server, valueAttr, val);
        }

        if (valueAttr) {
            scheduler_targetValueChanged(self->scheduler, (ModelNode*)valueAttr, val, q, currentTime);
        }
        else {
            scheduler_targetValueChanged(self->scheduler, self->controlEntity, val, q, currentTime);
        }
        
    }
}

static void
scheduleController_updateCurrentValue(ScheduleController self, ScheduleTargetType targetType, MmsValue* val, uint64_t currentTime)
{
    DataAttribute* valueAttr = NULL;
    DataAttribute* qAttr = NULL;
    DataAttribute* tAttr = NULL;

    DataObject* valueObj = NULL;

    char* objNameStr = NULL;

    if (targetType != SCHD_TYPE_UNKNOWN) {
        if (targetType == SCHD_TYPE_MV) {
            objNameStr = "ValMV";
        }
        else if (targetType == SCHD_TYPE_ENS) {
            objNameStr = "ValENS";
        }
        else if (targetType == SCHD_TYPE_INS) {
            objNameStr = "ValINS";
        }
        else if (targetType == SCHD_TYPE_SPS) {
            objNameStr = "ValSPS";
        }
        else {
            return;
        }

        valueObj = (DataObject*)ModelNode_getChild((ModelNode*)self->controllerLn, objNameStr);
    }
    else {
        if (valueObj == NULL)
            valueObj = (DataObject*)ModelNode_getChild((ModelNode*)self->controllerLn, "ValMV");
        else if (valueObj == NULL)
            valueObj = (DataObject*)ModelNode_getChild((ModelNode*)self->controllerLn, "ValENS");
        else if (valueObj == NULL)
            valueObj = (DataObject*)ModelNode_getChild((ModelNode*)self->controllerLn, "ValINS");
        else if (valueObj == NULL)
            valueObj = (DataObject*)ModelNode_getChild((ModelNode*)self->controllerLn, "ValSPS");
    }

    if (valueObj) {

        char objRefBuf[130];

        ModelNode_getObjectReference((ModelNode*)valueObj, objRefBuf);

        char valueBuf[200];

        MmsValue_printToBuffer(val, valueBuf, 200);

        printf("INFO: Update %s -> %s\n", objRefBuf, valueBuf);

        if (targetType == SCHD_TYPE_MV) {
            valueAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)valueObj, "mag.f");

            if (valueAttr == NULL)
                valueAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)valueObj, "mag.i");
        }
        else {
            valueAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)valueObj, "stVal");
        }

        qAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)valueObj, "q");
        tAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)valueObj, "t");

        IedServer_lockDataModel(self->server);

        if (valueAttr && val) {
            IedServer_updateAttributeValue(self->server, valueAttr, val);
             if (qAttr) IedServer_updateQuality(self->server, qAttr, QUALITY_VALIDITY_GOOD);
        }
        else {
            if (qAttr) IedServer_updateQuality(self->server, qAttr, QUALITY_VALIDITY_INVALID);
        }

        if (tAttr) IedServer_updateUTCTimeAttributeValue(self->server, tAttr, currentTime);

        IedServer_unlockDataModel(self->server);
    }

    return;
}


/* functions called by Schedule */

/**
 * @brief Schedule informs the controller that its priority was updated
 * 
 * @param self 
 * @param sched 
 * @param newPrio 
 */
void
scheduleController_schedulePrioUpdated(ScheduleController self, Schedule sched, int newPrio)
{
    Schedule activeSchedule = scheduleController_getActiveSchedule(self);

    if (activeSchedule) {
        if (activeSchedule != self->activeSchedule) {
            
            // change active schedule
            self->activeSchedule = activeSchedule;

            printf("INFO: Active schedule changed due to priority update\n");
            scheduleController_updateActSchdRef(self, self->activeSchedule);
        }
    }
    else {
        // there is no running schedule
        scheduleController_updateActSchdRef(self, NULL);
    }
}

/**
 * @brief Schedule informs the controller that its state was updated
 * 
 * @param self 
 * @param sched 
 * @param newState 
 */
void
scheduleController_scheduleStateUpdated(ScheduleController self, Schedule sched, ScheduleState newState)
{
    Schedule activeSchedule = scheduleController_getActiveSchedule(self);

    if (activeSchedule) {
        if (activeSchedule != self->activeSchedule) {

            printf("INFO: Active schedule changed %s -> %s\n", 
                self->activeSchedule ? self->activeSchedule->scheduleLn->name : "", activeSchedule->scheduleLn->name);
            
            // change active schedule
            self->activeSchedule = activeSchedule;

            // get current value from new running schedule

            MmsValue* outputValue = Schedule_getCurrentValue(activeSchedule);

            char valueBuf[100];

            MmsValue_printToBuffer(outputValue, valueBuf, 100);

            printf("INFO: New value %s\n", valueBuf);

            scheduleController_updateActSchdRef(self, self->activeSchedule);
            scheduleController_updateCurrentValue(self, activeSchedule->targetType, outputValue, Hal_getTimeInMs());
            scheduleController_updateTargetValue(self,  activeSchedule->targetType, outputValue, Hal_getTimeInMs());
        }
    }
    else {
        // there is no running schedule
        scheduleController_updateActSchdRef(self, NULL);
        scheduleController_updateCurrentValue(self, SCHD_TYPE_UNKNOWN, NULL, Hal_getTimeInMs());
        scheduleController_updateTargetValue(self,  SCHD_TYPE_UNKNOWN, NULL, Hal_getTimeInMs());
        self->activeSchedule = NULL;
    }
}
/**
 * @brief Schedule informs the controller that its scheduled value was updated
 * 
 * @param self 
 * @param sched 
 */
void
scheduleController_scheduleValueUpdated(ScheduleController self, Schedule sched, MmsValue* val, uint64_t timestamp)
{
    // check if the schedule is the actve schedule

    if (sched == self->activeSchedule) {
        scheduleController_updateCurrentValue(self, sched->targetType, val, timestamp);
        scheduleController_updateTargetValue(self, sched->targetType, val, timestamp);
    }
    else {
        //ignore new value
    }
}

ScheduleController
ScheduleController_create(LogicalNode* fsccLn, Scheduler scheduler)
{
    ScheduleController self = (ScheduleController)calloc(1, sizeof(struct sScheduleController));

    if (self) {
        self->controllerLn = fsccLn;
        self->server = scheduler->server;
        self->model = scheduler->model;
        self->scheduler = scheduler;
        self->schedules = LinkedList_create();
        self->controlEntity = NULL;
    }

    return self;
}

void
ScheduleController_destroy(ScheduleController self)
{
    if (self) {

        LinkedList_destroyStatic(self->schedules);

        free(self);
    }
}

static ModelNode*
scheduleController_lookUpTargetObject(ScheduleController self, const char* targetRef)
{
    //TODO implement

    if (targetRef && targetRef[0] != 0) {

        bool withoutIedName = false;

        if (targetRef[0] == '@') {
            withoutIedName = true;
            targetRef = targetRef + 1;
        }

        ModelNode* targetNode = IedModel_getModelNodeByShortObjectReference(self->model, targetRef);


        if (targetNode->modelType == DataObjectModelType) {
            //TODO check for stVal or mxVal
        }
        else if (targetNode->modelType == DataAttributeModelType) {
            //TODO check if it is of the correct basic type of contructed type
        }


        return targetNode;
    }
    else {
        printf("INFO: CtlEnt value is empty\n");
    }

    //lookup target object

    //check if object type is correct

    return NULL;
}

static int
scheduleController_getNumberOfScheduleReferences(ScheduleController self)
{
    int scheduleRefCount = 0;

    LinkedList dataObjects = ModelNode_getChildren((ModelNode*)self->controllerLn);

    LinkedList doElem = LinkedList_getNext(dataObjects);

    while (doElem) {
        DataObject* dObj = (DataObject*)LinkedList_getData(doElem);

        if (scheduler_checkIfMultiObjInst(dObj->name, "Schd")) {
            scheduleRefCount++;
        }

        doElem = LinkedList_getNext(doElem);
    }

    LinkedList_destroyStatic(dataObjects);

    printf("INFO: ScheduleController has %i Schd references\n", scheduleRefCount);

    return scheduleRefCount;
}

DataAttribute*
ScheduleController_getScheduleReferenceWithIdx(ScheduleController self, int idx)
{
    idx++;

    DataAttribute* valueAttr = NULL;

    char attrNameBuf[100];

    char* multiObjStr = "Schd";

    sprintf(attrNameBuf, "%s%i.setSrcRef", multiObjStr, idx);
    valueAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)self->controllerLn, attrNameBuf);

    if (valueAttr == NULL) {
        sprintf(attrNameBuf, "%s%02i.setSrcRef", multiObjStr, idx);
        valueAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)self->controllerLn, attrNameBuf);
    }

    if (valueAttr == NULL) {
        sprintf(attrNameBuf, "%s%03i.setSrcRef", multiObjStr, idx);
        valueAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)self->controllerLn, attrNameBuf);
    }

    if (valueAttr == NULL) {
        sprintf(attrNameBuf, "%s%04i.setSrcRef", multiObjStr, idx);
        valueAttr = (DataAttribute*)ModelNode_getChild((ModelNode*)self->controllerLn, attrNameBuf);
    };

    return valueAttr;
}

void
ScheduleController_setCtlEnt(ScheduleController self, const char* ctlEntValue)
{
    DataAttribute* ctlEnt_setSrcRef = (DataAttribute*)ModelNode_getChild((ModelNode*)self->controllerLn, "CtlEnt.setSrcRef");

    if (ctlEnt_setSrcRef) {
        ModelNode* targetObject = scheduleController_lookUpTargetObject(self, ctlEntValue);

        if (targetObject) {
            MmsValue_setVisibleString(ctlEnt_setSrcRef->mmsValue, ctlEntValue);
        }
        else {
            printf("ERROR: ScheduleController_setCtlEnt - target object %s not found!\n", ctlEntValue);
        }
    }
    else {
        printf("ERROR: ScheduleController_setCtlEnt - CtlEnt.setSrcRef not found!\n");
    }

}

bool
ScheduleController_setSchdRef(ScheduleController self, const char* id, const char* ref)
{
    DataAttribute* schd = (DataAttribute*)ModelNode_getChild((ModelNode*)self->controllerLn, id);

    if (schd) {
        DataAttribute* schd_setSrcRef = (DataAttribute*)ModelNode_getChild((ModelNode*)schd, "setSrcRef");

        if (schd_setSrcRef) {
            MmsValue_setVisibleString(schd_setSrcRef->mmsValue, ref);
        }
        else {
            printf("ERROR: schedule reference %s not found in schedule controller\n", ref);

            return false;
        }
    }
    else {
        printf("ERROR: schedule reference %s not found in schedule controller\n", ref);

        return false;
    }

    return true;
}

static MmsDataAccessError
ctlEnt_setSrcRef_writeAccessHandler(DataAttribute* dataAttribute, MmsValue* value, ClientConnection connection, void* parameter)
{
    ScheduleController self = (ScheduleController)parameter;

    const char* targetRef = MmsValue_toString(value);
    printf("INFO:   -> control entity: %s\n", targetRef);

    ModelNode* targetObject = scheduleController_lookUpTargetObject(self, targetRef);

    if (targetObject) {
        self->controlEntity = targetObject;
        printf("INFO: control entity set: %s\n", targetRef);
    }
    else {
        printf("ERROR: %s is no valid control entity\n", targetRef);

        return DATA_ACCESS_ERROR_OBJECT_VALUE_INVALID;
    }

    IedServer_updateAttributeValue(self->server, dataAttribute, value);

    if (self->storage) {
        SchedulerStorage_saveScheduleController(self->storage, self);
    }

    return DATA_ACCESS_ERROR_SUCCESS_NO_UPDATE;
}

static MmsDataAccessError
schd_setSrcRef_writeAccessHandler(DataAttribute* dataAttribute, MmsValue* value, ClientConnection connection, void* parameter)
{
    ScheduleController self = (ScheduleController)parameter;

    const char* scheduleRef = MmsValue_toString(value);
    printf("INFO:   -> schedule: %s\n", scheduleRef);

    Schedule sched = Scheduler_getScheduleByObjRef(self->scheduler, scheduleRef);

    if (sched) {
        printf("INFO:       -> schedule found\n");
    }
    else {
        printf("ERROR: schedule %s not found\n", scheduleRef);
        return DATA_ACCESS_ERROR_OBJECT_VALUE_INVALID;
    }

    if (LinkedList_contains(self->schedules, sched)) {
        printf("ERROR: schedule %s already conntected with schedule controller\n", scheduleRef);
        return DATA_ACCESS_ERROR_OBJECT_VALUE_INVALID;
    }

    if (dataAttribute->mmsValue) {
        const char* oldScheduleRef = MmsValue_toString(dataAttribute->mmsValue);

        Schedule oldSchedule = Scheduler_getScheduleByObjRef(self->scheduler, oldScheduleRef);

        if (oldSchedule) {
            printf("WARNING: disconnect schedule %s from schedule controller\n", oldScheduleRef);

            //TODO how to handle the situation when multiple Schd have the same reference?

            //TODO remove listener??? (or remove automatically when called from unknown schedule?)

            LinkedList_remove(self->schedules, oldSchedule);
        }
    }

    printf("INFO: connect schedule %s to schedule controller\n", scheduleRef);

    LinkedList_add(self->schedules, sched);

    Schedule_setListeningController(sched, self);

    IedServer_updateAttributeValue(self->server, dataAttribute, value);

    if (self->storage) {
        SchedulerStorage_saveScheduleController(self->storage, self);
    }
    
    return DATA_ACCESS_ERROR_SUCCESS_NO_UPDATE;
}

int
ScheduleController_getRefCount(ScheduleController self)
{
    return scheduleController_getNumberOfScheduleReferences(self);
}

void
ScheduleController_initialize(ScheduleController self)
{
    /* create list of referenced (known) schedules */

    LinkedList dataObjects = ModelNode_getChildren((ModelNode*)self->controllerLn);

    LinkedList doElem = LinkedList_getNext(dataObjects);

    while (doElem) {
        DataObject* dObj = (DataObject*)LinkedList_getData(doElem);

        if (scheduler_checkIfMultiObjInst(dObj->name, "Schd")) {
            DataAttribute* schd_setSrcRef = (DataAttribute*)ModelNode_getChild((ModelNode*)dObj, "setSrcRef");

            if (schd_setSrcRef) {
                if (schd_setSrcRef->type == IEC61850_VISIBLE_STRING_129) {
                    printf("INFO: %s.setSrcRef found\n", dObj->name);
                
                    if (schd_setSrcRef->mmsValue) {
                        const char* scheduleRef = MmsValue_toString(schd_setSrcRef->mmsValue);
                        printf("INFO:   -> schedule: %s\n", scheduleRef);

                        Schedule sched = Scheduler_getScheduleByObjRef(self->scheduler, scheduleRef);

                        printf("INFO:       -> schedule found\n");

                        LinkedList_add(self->schedules, sched);

                        Schedule_setListeningController(sched, self);
                    }

                    IedServer_handleWriteAccess(self->server, schd_setSrcRef, schd_setSrcRef_writeAccessHandler, self);
                }
                else {
                    printf("ERROR: %s.setSrcRef has wrong type\n", dObj->name);
                }
            }
            else {
                printf("ERROR: %s has no attribute setSrcRef\n", dObj->name);
            }
        }
        else if (!strcmp(dObj->name, "CtlEnt")) {
            DataAttribute* ctlEnt_setSrcRef = (DataAttribute*)ModelNode_getChild((ModelNode*)dObj, "setSrcRef");

            if (ctlEnt_setSrcRef) {
                if (ctlEnt_setSrcRef->type == IEC61850_VISIBLE_STRING_129) {
                    printf("INFO: %s.setSrcRef found\n", dObj->name);

                    ModelNode* ctlEntity = NULL;

                    if (ctlEnt_setSrcRef->mmsValue) {
                        const char* targetRef = MmsValue_toString(ctlEnt_setSrcRef->mmsValue);

                        ctlEntity = scheduleController_lookUpTargetObject(self, targetRef);
                    }

                    self->controlEntity = ctlEntity;

                    IedServer_handleWriteAccess(self->server, ctlEnt_setSrcRef, ctlEnt_setSrcRef_writeAccessHandler, self);
                }
                else {
                    printf("ERROR: %s.setSrcRef has wrong type\n", dObj->name);
                }

            }
            else {
                printf("ERROR: %s has not attribute setSrcRef\n", dObj->name);
            }
        }

        doElem = LinkedList_getNext(doElem);
    }

    LinkedList_destroyStatic(dataObjects);

    /* initialized ActSchdRef */

    Schedule activeSchedule = scheduleController_getActiveSchedule(self);
    scheduleController_updateActSchdRef(self, activeSchedule);
}
