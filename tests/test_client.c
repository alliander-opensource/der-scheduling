#include <libiec61850/iec61850_client.h>
#include <libiec61850/hal_time.h>
#include <stdio.h>

int
main(int argc, char** argv)
{
    IedClientError err;

    IedConnection con = IedConnection_create();

    IedConnection_connect(con, &err, "localhost", 102);

    if (err == IED_ERROR_OK) {

        /* configure reserve schedule */
        IedConnection_writeInt32Value(con, &err, "DER_Scheduler_Control/ActPow_Res_FSCH01.SchdPrio.setVal", IEC61850_FC_SP, 0);
        IedConnection_writeInt32Value(con, &err, "DER_Scheduler_Control/ActPow_Res_FSCH01.NumEntr.setVal", IEC61850_FC_SP, 96);
        IedConnection_writeInt32Value(con, &err, "DER_Scheduler_Control/ActPow_Res_FSCH01.SchdIntv.setVal", IEC61850_FC_SP, 60 * 15);

        for (int i = 0; i < 96; i++) {
            char objRefBuf[130];
            sprintf(objRefBuf, "DER_Scheduler_Control/ActPow_Res_FSCH01.ValASG%03i.setMag.f", i + 1);

            IedConnection_writeFloatValue(con, &err, objRefBuf, IEC61850_FC_SP, (float)10);

            if (err != IED_ERROR_OK) {
                printf("ERROR: failed to set %s\n", objRefBuf);
            }
        }

        
        IedConnection_writeUnsigned32Value(con, &err, "DER_Scheduler_Control/ActPow_Res_FSCH01.StrTm01.setCal.occ", IEC61850_FC_SP, 0); /* occ = Time(0) */
        IedConnection_writeInt32Value(con, &err, "DER_Scheduler_Control/ActPow_Res_FSCH01.StrTm01.setCal.occType", IEC61850_FC_SP, 1);  /* occPer = Day(1) */
        IedConnection_writeUnsigned32Value(con, &err, "DER_Scheduler_Control/ActPow_Res_FSCH01.StrTm01.setCal.hr", IEC61850_FC_SP, 0); /* hr = 0 */

        //TODO enable reserve schedule

        /* configure schedule */

        IedConnection_writeInt32Value(con, &err, "DER_Scheduler_Control/ActPow_FSCH01.SchdPrio.setVal", IEC61850_FC_SP, 10);

        if (err != IED_ERROR_OK) {
            printf("ERROR: failed to set schedule priority\n");
        }

        IedConnection_writeInt32Value(con, &err, "DER_Scheduler_Control/ActPow_FSCH01.NumEntr.setVal", IEC61850_FC_SP, 4);

        if (err != IED_ERROR_OK) {
            printf("ERROR: failed to set schedule number of entries\n");
        }

        IedConnection_writeInt32Value(con, &err, "DER_Scheduler_Control/ActPow_FSCH01.SchdIntv.setVal", IEC61850_FC_SP, 2);

        if (err != IED_ERROR_OK) {
            printf("ERROR: failed to set SchdIntv.setVal\n");
        }

        for (int i = 0; i < 100; i++) {
            char objRefBuf[130];
            sprintf(objRefBuf, "DER_Scheduler_Control/ActPow_FSCH01.ValASG%03i.setMag.f", i + 1);

            IedConnection_writeFloatValue(con, &err, objRefBuf, IEC61850_FC_SP, (float)i);

            if (err != IED_ERROR_OK) {
                printf("ERROR: failed to set %s\n", objRefBuf);
            }
        }

        MmsValue* strTmVal = MmsValue_newUtcTimeByMsTime(Hal_getTimeInMs() + 3000);

        IedConnection_writeObject(con, &err, "DER_Scheduler_Control/ActPow_FSCH01.StrTm01.setTm", IEC61850_FC_SP, strTmVal);

        if (err != IED_ERROR_OK) {
            printf("ERROR: failed to set DER_Scheduler_Control/ActPow_FSCH01.StrTm01.setTm\n");
        }

        MmsValue_delete(strTmVal);

        strTmVal = MmsValue_newUtcTimeByMsTime(Hal_getTimeInMs() + 15000);

        IedConnection_writeObject(con, &err, "DER_Scheduler_Control/ActPow_FSCH01.StrTm02.setTm", IEC61850_FC_SP, strTmVal);

        if (err != IED_ERROR_OK) {
            printf("ERROR: failed to set DER_Scheduler_Control/ActPow_FSCH01.StrTm01.setTm\n");
        }

        MmsValue_delete(strTmVal);

        strTmVal = MmsValue_newUtcTimeByMsTime(Hal_getTimeInMs() + 25000);

        IedConnection_writeObject(con, &err, "DER_Scheduler_Control/ActPow_FSCH01.StrTm03.setTm", IEC61850_FC_SP, strTmVal);

        if (err != IED_ERROR_OK) {
            printf("ERROR: failed to set DER_Scheduler_Control/ActPow_FSCH01.StrTm01.setTm\n");
        }

        MmsValue_delete(strTmVal);

        //TODO enable schedule
        ControlObjectClient control
            = ControlObjectClient_create("DER_Scheduler_Control/ActPow_FSCH01.EnaReq", con);

        MmsValue* ctlVal = MmsValue_newBoolean(true);

        ControlObjectClient_setOrigin(control, NULL, 3);

        if (ControlObjectClient_operate(control, ctlVal, 0 /* operate now */)) {
            printf("DER_Scheduler_Control/ActPow_FSCH01.EnaReq operated successfully\n");
        }
        else {
            printf("failed to operate DER_Scheduler_Control/ActPow_FSCH01.EnaReq\n");
        }

        MmsValue_delete(ctlVal);

        ControlObjectClient_destroy(control);

        IedConnection_close(con);
    }
    else {
        printf("ERROR: Failed to connect\n");
    }

    IedConnection_destroy(con);
}