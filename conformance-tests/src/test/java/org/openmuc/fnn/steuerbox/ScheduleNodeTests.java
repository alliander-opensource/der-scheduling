/*
 * Copyright 2023 Fraunhofer ISE
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

package org.openmuc.fnn.steuerbox;

import com.beanit.iec61850bean.BasicDataAttribute;
import com.beanit.iec61850bean.Fc;
import com.beanit.iec61850bean.FcModelNode;
import com.beanit.iec61850bean.ServiceError;
import de.fhg.ise.testtool.utils.annotations.label.Requirements;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openmuc.fnn.steuerbox.scheduling.PreparedSchedule;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleDefinitions;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleEnablingErrorKind;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleState;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openmuc.fnn.steuerbox.AllianderBaseTest.MandatoryOnCondition.ifPresent;
import static org.openmuc.fnn.steuerbox.models.Requirement.LN01;
import static org.openmuc.fnn.steuerbox.models.Requirement.LN03;
import static org.openmuc.fnn.steuerbox.models.Requirement.S08;
import static org.openmuc.fnn.steuerbox.models.Requirement.S13;
import static org.openmuc.fnn.steuerbox.models.Requirement.S14;
import static org.openmuc.fnn.steuerbox.models.Requirement.S15;

/**
 * Holds tests related to 61850 schedule node behaviour
 */
public class ScheduleNodeTests extends AllianderBaseTest {

    private static final Logger log = LoggerFactory.getLogger(ScheduleNodeTests.class);

    @Requirements(value = { LN03, LN01 },
            description = "Test if the scheduler has the required nodes with correct types as defined in IEC 61850-90-10:2017, table 7 (page 26)")
    @ParameterizedTest(name = "hasRequiredSubNodes running {0}")
    @MethodSource("getAllSchedules")
    <X> void hasRequiredSubNodes(ScheduleDefinitions<X> scheduleConstants) {

        // relevant part of the table is the "non-derived-statistics" (nds) column as there are no derived-statistic

        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {
            log.info("Testing Schedule {}", scheduleName);

            //  Collection of Optional nodes
            Map<String, Fc> optional = new HashMap<>();

            // Collection of mandatory nodes that must be present
            Map<String, Fc> mandatory = new HashMap<>();

            // Collection of 'AtMostOne' nodes
            Map<String, Fc> atMostOne = new HashMap<>();

            //in IEC61850 90-10_2017_end.pdf , table 6, PresConds: MmultiF(), presence under condition
            Collection<MandatoryOnCondition> mMultiF = new LinkedList<>();

            // Collection of Omulti elements (one or several nodes may be present)
            Map<String, Fc> oMulti = new HashMap<>();

            /**
             *  Descriptions (DC)
             */
            optional.put("NamPlt", Fc.DC);

            /**
             *  Status information (ST)
             */
            mandatory.put("SchdSt", Fc.ST);
            optional.put("SchdEntr", Fc.ST);
            atMostOne.put("ValINS", Fc.ST);
            atMostOne.put("ValSPS", Fc.ST);
            atMostOne.put("ValENS", Fc.ST);
            optional.put("ActStrTm", Fc.ST);
            mandatory.put("NxtStrTm", Fc.ST);
            mandatory.put("SchdEnaErr", Fc.ST);
            mandatory.put("Beh", Fc.ST);
            optional.put("Health", Fc.ST);
            optional.put("Mir", Fc.ST);
            mandatory.put("EnaReq", Fc.ST);
            mandatory.put("DsaReq", Fc.ST);

            /**
             *  Measured and metered values (MX)
             */
            atMostOne.put("ValMV", Fc.MX);

            /**
             * Controls (CO)
             */
            optional.put("Mod", Fc.CO);

            /**
             * Settings (SP)
             */
            optional.put("SchdPrio", Fc.SP);
            mandatory.put("NumEntr", Fc.SP);
            mandatory.put("SchdIntv", Fc.SP);
            //at least one element needs to be present if ValMV is present, otherwise forbidden
            mMultiF.add(ifPresent("ValMV").thenMandatory("ValASG001", Fc.SP));
            //at least one element needs to be present if ValINS is present, otherwise forbidden
            mMultiF.add(ifPresent("ValINS").thenMandatory("ValING001", Fc.SP));
            //at least one element needs to be present if ValISPS is present, otherwise forbidden
            mMultiF.add(ifPresent("ValSPS").thenMandatory("ValSPG001", Fc.SP));
            //at least one element needs to be present if ValENS is present, otherwise forbidden
            mMultiF.add(ifPresent("ValENS").thenMandatory("ValENG001", Fc.SP));
            oMulti.put("StrTm", Fc.SP);
            optional.put("EvTrg", Fc.SP);
            //needs to be present if EvTrg is present, otherwise optional
            mMultiF.add(ifPresent("EvTrg").thenMandatory("InSyn", Fc.SP));
            mandatory.put("SchdReuse", Fc.SP);
            oMulti.put("InRef", Fc.SP);

            Collection<String> violations = new LinkedList<>();
            violations.addAll(testOptionalNodes(optional, scheduleName));
            violations.addAll(testMandatoryNodes(mandatory, scheduleName));
            violations.addAll(testAtMostOnceNodes(atMostOne, scheduleName));
            violations.addAll(testMMultiF(mMultiF, scheduleName));
            violations.addAll(testOMulti(oMulti, scheduleName));

            String delimiter = "\n - ";
            String violationsList = delimiter + String.join(delimiter, violations);

            assertTrue(violations.isEmpty(),
                    "Found violations of node requirements for schedule " + scheduleName + ": " + violationsList);

        }
    }

    @Requirements(value = LN03,
            description = "Test that SchdEntr is present and is updated by the currently running schedule as described in  IEC 61850-90-10:2017, table 7 (page 26)")
    @ParameterizedTest(name = "SchdEntrIsUpdatedWithCurrentlyRunningScheduleIfPresent running {0}")
    @MethodSource("getAllSchedules")
    <X> void SchdEntrIsUpdatedWithCurrentlyRunningScheduleIfPresent(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {
        // test if SchdEntr is available

        // test all 10 schedules of one scheduleConstant one by one
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {

            //disable the schedule and display the name
            dut.disableSchedule(scheduleName);
            log.info(scheduleName);

            //check if optional SchEntr is present
            boolean isPresent = dut.nodeExists(scheduleName + ".SchdEntr");
            if (isPresent) {

                //display the state of the schedule
                log.info("State of the schedule: " + dut.getScheduleState(scheduleName).toString());

                //if state of the schedule is not RUNNING SchdEntr might be 0
                if (dut.getScheduleState(scheduleName) != ScheduleState.RUNNING) {
                    String notRunningSchdEntrValueAsString = dut.getNodeEntryasString(scheduleName, "SchdEntr",
                            "stVal");
                    Assertions.assertEquals("0", notRunningSchdEntrValueAsString,
                            "SchdEntry is not 0 although schedule is not running");
                }

                //now write a schedule and check if SchdEntry is not 0
                PreparedSchedule preparedSchedule = scheduleConstants.prepareSchedule(
                        scheduleConstants.getDefaultValues(1), scheduleConstants.getScheduleNumber(scheduleName),
                        ofSeconds(8), Instant.now().plusMillis(500), 20);
                dut.writeAndEnableSchedule(preparedSchedule);
                Thread.sleep(4000);
                String runningSchdEntrValueAsString = dut.getNodeEntryasString(scheduleName, "SchdEntr", "stVal");
                Assertions.assertNotEquals("0", runningSchdEntrValueAsString,
                        "SchdEntry is 0 although schedule is running");
            }
            else {
                log.info("Optional node SchdEntr not found, skipping test");
            }
        }
    }

    /**
     * INS = Integer status; we do not have integer schedules thus we ValINS should not be present Test that ValINS is
     * not present (because it is not relevant for our use case thus we cannot/do not want to test the expected
     * behaviour). IEC 61850-90-10:2017, table 7 (page 26)
     **/
    @Requirements(value = LN03,
            description = "Test that ValINS is not available")
    @ParameterizedTest(name = "ValINSIsUpdatedWithCurrentlyRunningScheduleIfPresent running {0}")
    @MethodSource("getAllSchedules")
    void ValINSIsUpdatedWithCurrentlyRunningScheduleIfPresent(ScheduleDefinitions scheduleConstants) {
        testOptionalNodeNotPresent(scheduleConstants, "ValINS");
    }

    /**
     * SPS = single point status, we assume that it is the readout value of boolean schedules Test that ValSPS is
     * present when having a boolean schedule, in this case it should behave like defined in IEC 61850-90-10:2017, table
     * 7 (page 26) if we don't have a boolean schedule we have a float schedule; in this case test that ValMV is present
     * and that it behaves like defined in the same table
     **/
    @Requirements(value = LN03,
            description = "Test ValSPS/ValMv behaves as defined in IEC 61850-90-10:2017, table 7 (page 26) ")
    @ParameterizedTest(name = "ValSpsIsUpdatedWithCurrentlyRunningScheduleIfPresent running {0}")
    @MethodSource("getAllSchedules")
    <X> void ValSpsOrValMvIsUpdatedWithCurrentlyRunningSchedule(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {

        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {

            PreparedSchedule schedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(2), Instant.now().plusMillis(500), 20);

            if (ScheduleType.SPG.equals(scheduleConstants.getScheduleType())) {

                //test, that node ValSPS is present
                assertTrue(dut.nodeExists(scheduleName + ".ValSPS"));
                testOptionalNodeNotPresent(scheduleConstants, "ValMV");

                //initial valid status
                dut.writeAndEnableSchedule(schedule);
                Thread.sleep(1500);
                dut.disableSchedules(scheduleName);

                //if schedule is inactive, quality of ValSPS should be set to invalid
                Assertions.assertEquals("INVALID", dut.getNodeEntryasString(scheduleName, "ValSPS", "q"));

                //if schedule is active, ValSPS should hold the current value determined by the schedule
                dut.writeAndEnableSchedule(schedule);
                List<Boolean> actualValues = dut.monitor(Instant.now(), ofSeconds(2), ofSeconds(2), scheduleConstants);
                List<Boolean> expectedValues = Arrays.asList(true);
                assertValuesMatch(expectedValues, actualValues);
            }
            else {
                // float schedule
                assertTrue(dut.nodeExists(scheduleName + ".ValMV"));
                testOptionalNodeNotPresent(scheduleConstants, "ValSPS");

                //initial valid status
                dut.writeAndEnableSchedule(schedule);
                Thread.sleep(1500);
                dut.disableSchedules(scheduleName);

                //if schedule is inactive, quality of ValMV should be set to invalid
                Assertions.assertEquals("INVALID", dut.getNodeEntryasString(scheduleName, "ValMV", "q"));

                //activate schedule
                Instant timestamp = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
                PreparedSchedule preparedSchedule = scheduleConstants.prepareSchedule(
                        scheduleConstants.getDefaultValues(1), scheduleConstants.getScheduleNumber(scheduleName),
                        ofSeconds(8), timestamp, 20);
                dut.writeAndEnableSchedule(preparedSchedule);

                //monitor returns the ValMV-values, need to be the one we set with the schedule
                List<Float> actualValues = dut.monitor(timestamp, ofSeconds(8), ofSeconds(8), scheduleConstants);
                List<Float> expectedValues = Arrays.asList(0.0f);
                assertValuesMatch(expectedValues, actualValues, 0.1);
            }
        }
    }

    /**
     * ENS = enumerated status, we do not have a schedule with enumerated value thus we can not have the node ValENS
     * Test that ValENS is not present (because it is not relevant for our use case  thus we cannot/do not want to test
     * the expected behaviour). IEC 61850-90-10:2017, table 7 (page 26)
     **/
    @Requirements(value = LN03,
            description = "Test that ValENS is not available")
    @ParameterizedTest(name = "ValEnsIsUpdatedWithCurrentlyRunningScheduleIfPresent running {0}")
    @MethodSource("getAllSchedules")
    void ValEnsIsUpdatedWithCurrentlyRunningScheduleIfPresent(ScheduleDefinitions<?> scheduleConstants) {
        testOptionalNodeNotPresent(scheduleConstants, "ValENS");
    }

    @Requirements(value = LN03,
            description = "Test if optional node ActStrTm is present it should behave like defined in IEC 61850-90-10:2017, table 7 (page 26)")
    @ParameterizedTest(name = "ActStrTmIsUpdatedProperly running {0}")
    @MethodSource("getAllSchedules")
    <X> void ActStrTmIsUpdatedProperly(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {
            if (dut.nodeExists(scheduleName + ".ActStrTm")) {
                PreparedSchedule schedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                        scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(2), Instant.now().plusMillis(500), 20);

                //initial status
                dut.writeAndEnableSchedule(schedule);
                Thread.sleep(1500);
                dut.disableSchedules(scheduleName);

                //if schedule is disabled, quality of ActStrTm should be invalid
                Assertions.assertEquals("INVALID", dut.getNodeEntryasString(scheduleName, "ActStrTm", "q"));

                //if schedule is active, ActStrTm.stVal should have the timestamp the active schedule started
                Instant timestamp = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
                PreparedSchedule preparedSchedule = scheduleConstants.prepareSchedule(
                        scheduleConstants.getDefaultValues(1), scheduleConstants.getScheduleNumber(scheduleName),
                        ofSeconds(4), timestamp, 20);
                dut.writeAndEnableSchedule(preparedSchedule);
                Thread.sleep(3000);
                Assertions.assertEquals(timestamp.toString(),
                        dut.getNodeEntryasString(scheduleName, "ActStrTm", "stVal"));
            }
        }
    }

    @Requirements(value = LN03,
            description = "Test if NxtStrTm is mandatory and should behave like defined in IEC 61850-90-10:2017, table 7 (page 26)")
    @ParameterizedTest(name = "NxtStrTmIsUpdatedProperly running {0}")
    @MethodSource("getAllSchedules")
    <X> void NxtStrTmIsUpdatedProperly(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {
            PreparedSchedule schedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(2), Instant.now().plusMillis(500), 20);

            //initial status
            dut.writeAndEnableSchedule(schedule);
            Thread.sleep(1500);
            dut.disableSchedules(scheduleName);

            //if schedule is disabled, quality of ActStrTm should be invalid
            Assertions.assertEquals("INVALID", dut.getNodeEntryasString(scheduleName, "NxtStrTm", "q"));

            //if schedule is active, ActStrTm.stVal should have the timestamp the active schedule started
            Instant timestamp = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
            PreparedSchedule preparedSchedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(2), timestamp, 20);
            dut.writeAndEnableSchedule(preparedSchedule);
            Assertions.assertEquals(timestamp.toString(), dut.getNodeEntryasString(scheduleName, "NxtStrTm", "stVal"));
        }
    }

    @Requirements(value = LN03,
            description =
                    "Test that EnaReq holds a reasonable error code after provoking errors whilst enabling schedules. "
                            + "See IEC 61850-90-10:2017, table 7 (page 26)")
    @ParameterizedTest(name = "EnaReq_operating running {0}")
    @MethodSource("getAllSchedules")
    <X> void EnaReq_operating(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {

            // intial: schedule has valid values set
            PreparedSchedule preparedSchedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(1), Instant.now().plusSeconds(100),
                    100);
            dut.writeAndEnableSchedule(preparedSchedule);
            dut.disableSchedule(scheduleName);
            Thread.sleep(2000);
            // test if intial state is correct: should be in inital state
            Assertions.assertEquals(ScheduleState.NOT_READY, dut.getScheduleState(scheduleName));

            // test 1: operating with value false should be possible the status should ignore that
            BasicDataAttribute enableOp = dut.findAndAssignValue(scheduleName + ".EnaReq.Oper.ctlVal", Fc.CO, "false");
            dut.operate((FcModelNode) enableOp.getParent().getParent());
            // still same state:
            Assertions.assertEquals(ScheduleState.NOT_READY, dut.getScheduleState(scheduleName));

            // test2: when operating with value true on .EnaReq.Oper.ctlVal and when integrity check passes, schedule should be in ready state
            enableOp = dut.findAndAssignValue(scheduleName + ".EnaReq.Oper.ctlVal", Fc.CO, "true");
            dut.operate((FcModelNode) enableOp.getParent().getParent());
            Assertions.assertEquals(ScheduleState.READY, dut.getScheduleState(scheduleName));
        }
    }

    @Requirements(value = LN03,
            description = "Test that DsaReq behaves as described in IEC 61850-90-10:2017, table 7 (page 26)")
    @ParameterizedTest(name = "DsaReq_operating running {0}")
    @MethodSource("getAllSchedules")
    <X> void DsaReq_operating(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {

        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {

            // intial: schedule has valid values set
            PreparedSchedule initialSchedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(1), Instant.now().plusSeconds(2), 100);
            dut.writeAndEnableSchedule(initialSchedule);
            dut.disableSchedule(scheduleName);
            Thread.sleep(2000);
            // test if initial state is correct: should be in NOT_READY
            Assertions.assertEquals(ScheduleState.NOT_READY, dut.getScheduleState(scheduleName));

            // test if operating with value false on DsaReq is ignored
            BasicDataAttribute disableOp = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO, "false");
            dut.operate((FcModelNode) disableOp.getParent().getParent());
            Assertions.assertEquals(ScheduleState.NOT_READY, dut.getScheduleState(scheduleName));

            // test, if in disabled state and operating DsaReq with true, state ist still NOT_READY
            disableOp = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO, "true");
            dut.operate((FcModelNode) disableOp.getParent().getParent());
            Assertions.assertEquals(ScheduleState.NOT_READY, dut.getScheduleState(scheduleName));

            // test, if in enabled state and operating DsaReq with true, state turns into NOT_READY
            PreparedSchedule updatedSchedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(5), Instant.now().plusSeconds(1), 100);
            dut.writeAndEnableSchedule(updatedSchedule);
            disableOp = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO, "true");
            dut.operate((FcModelNode) disableOp.getParent().getParent());
            Thread.sleep(2000);
            Assertions.assertEquals(ScheduleState.NOT_READY, dut.getScheduleState(scheduleName));
        }
    }

    @Requirements(value = { LN03, S08 },
            description = "Test that SchdEnaErr holds MISSING_VALID_NUMENTR when writing invalid value to NumEntr.setVal (see IEC 61850-90-10:2017, table 7 (page 26))")
    @ParameterizedTest(name = "schdEnaErr_HoldsMISSING_VALID_NUMENTRcorrectly running {0}")
    @MethodSource("getAllSchedules")
    <X> void schdEnaErr_HoldsMISSING_VALID_NUMENTRcorrectly(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {

            // intial: valid values in NumEntr, Schdintv, SchdValues and test that SchdEnaErr shows no error kind
            PreparedSchedule schedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(1), Instant.now().plusSeconds(2), 100);
            dut.writeAndEnableSchedule(schedule);
            Thread.sleep(200);
            Assertions.assertEquals(ScheduleEnablingErrorKind.NONE, dut.getSchdEnaErr(scheduleName));
            dut.disableSchedule(scheduleName);

            // provoke error  MISSING_VALID_NUMENTR by setting NumEntr = -1
            dut.setDataValues(scheduleName + ".NumEntr.setVal", null, "-1");
            BasicDataAttribute disableOp1 = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO,
                    "false");
            BasicDataAttribute enableOp1 = dut.findAndAssignValue(scheduleName + ".EnaReq.Oper.ctlVal", Fc.CO, "true");
            // operating will throw, we ignore that error
            dut.operate((FcModelNode) disableOp1.getParent().getParent());
            Executable executable1 = () -> {
                dut.operate((FcModelNode) enableOp1.getParent().getParent());
            };
            Assertions.assertThrows(ServiceError.class, executable1);
            Assertions.assertEquals(ScheduleEnablingErrorKind.MISSING_VALID_NUMENTR, dut.getSchdEnaErr(scheduleName));
        }
    }

    @Requirements(value = { LN03, S08 },
            description = "Test that SchdEnaErr holds MISSING_VALID_SCHDINTV when writing invalid value to SchdIntv.setVal (see IEC 61850-90-10:2017, table 7 (page 26))")
    @ParameterizedTest(name = "SchdEnaErrHoldsMISSING_VALID_SCHDINTVcorrectly running {0}")
    @MethodSource("getAllSchedules")
    <X> void SchdEnaErrHoldsMISSING_VALID_SCHDINTVcorrectly(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {

            // intial: valid values in NumEntr, Schdintv, SchdValues and test that SchdEnaErr shows no error kind
            PreparedSchedule schedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(1), Instant.now().plusSeconds(2), 100);
            dut.writeAndEnableSchedule(schedule);
            Thread.sleep(200);
            Assertions.assertEquals(ScheduleEnablingErrorKind.NONE, dut.getSchdEnaErr(scheduleName));
            dut.disableSchedule(scheduleName);

            // provoke error  MISSING_VALID_SCHDINTV by setting SchdIntv = -1
            dut.setDataValues(scheduleName + ".SchdIntv.setVal", null, "-1");
            BasicDataAttribute disableOp1 = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO,
                    "false");
            BasicDataAttribute enableOp1 = dut.findAndAssignValue(scheduleName + ".EnaReq.Oper.ctlVal", Fc.CO, "true");
            // operating will throw, we ignore that error
            dut.operate((FcModelNode) disableOp1.getParent().getParent());
            Executable executable1 = () -> {
                dut.operate((FcModelNode) enableOp1.getParent().getParent());
            };
            Assertions.assertThrows(ServiceError.class, executable1);
            Assertions.assertEquals(ScheduleEnablingErrorKind.MISSING_VALID_SCHDINTV, dut.getSchdEnaErr(scheduleName));
        }
    }

    /**
     * This test exists only for float values, there is no possibility to create such a test for boolean schedules (as
     * there are no invalid values for boolean)
     */
    @Requirements(value = { LN03, S08 },
            description = "Tests that SchdEnaErr holds the error code MISSING_VALID_SCHEDULE_VALUE when invalid values are written, for (Max)Power Schedules (see IEC 61850-90-10:2017, table 7 (page 26))")
    @ParameterizedTest(name = "SchdEnaErrHoldsMISSING_VALID_SCHEDULE_VALUEScorrectlyFloatValues running {0}")
    @MethodSource("getPowerValueSchedules")
    void SchdEnaErrHoldsMISSING_VALID_SCHEDULE_VALUEScorrectly(ScheduleDefinitions<Number> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {

            // intial: valid values in SchdValues, test that it shows no error kind
            PreparedSchedule schedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                    scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(1), Instant.now().plusSeconds(2), 100);
            dut.writeAndEnableSchedule(schedule);
            Thread.sleep(200);
            Assertions.assertEquals(ScheduleEnablingErrorKind.NONE, dut.getSchdEnaErr(scheduleName));
            dut.disableSchedule(scheduleName);

            //Provoke MISSING_VALID_SCHEDULE_VALUES error kind by writing invalid values
            Executable excecutable = () -> {
                dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(
                        Arrays.asList(Float.NaN, Float.MAX_VALUE, Float.POSITIVE_INFINITY), 1, ofSeconds(2),
                        Instant.now().plusSeconds(1), 100));
            };
            Assertions.assertThrows(ServiceError.class, excecutable);
            Assertions.assertEquals(ScheduleEnablingErrorKind.MISSING_VALID_SCHEDULE_VALUES,
                    dut.getSchdEnaErr(scheduleName));
        }
    }

    @Requirements(value = LN03,
            description =
                    "Test that NumEntr can only be set to values > 0  and values <= the number of  instantiated Val[ASG|ING|SPG|ENG]'s "
                            + "as stated in IEC 61850-90-10:2017, table 7 (page 26)")
    @ParameterizedTest(name = "NumEntr_range running {0}")
    @MethodSource("getAllSchedules")
    <X> void NumEntr_range(ScheduleDefinitions<X> scheduleConstants)
            throws ServiceError, IOException, InterruptedException {

        String scheduleName = scheduleConstants.getScheduleName(1);
        // intial: valid values in NumEntr
        PreparedSchedule schedule = scheduleConstants.prepareSchedule(scheduleConstants.getDefaultValues(1),
                scheduleConstants.getScheduleNumber(scheduleName), ofSeconds(1), Instant.now().plusSeconds(2), 100);
        dut.writeAndEnableSchedule(schedule);
        Thread.sleep(200);
        dut.disableSchedule(scheduleName);

        //test: 0 can not bet set to NumEtr
        dut.setDataValues(scheduleName + ".NumEntr.setVal", null, "0");
        BasicDataAttribute disableOp = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO, "false");
        BasicDataAttribute enableOp = dut.findAndAssignValue(scheduleName + ".EnaReq.Oper.ctlVal", Fc.CO, "true");
        dut.operate((FcModelNode) disableOp.getParent().getParent());
        Executable executable = () -> dut.operate((FcModelNode) enableOp.getParent().getParent());
        Assertions.assertThrows(ServiceError.class, executable);

        //test: -1 can not bet set to NumEtr
        dut.setDataValues(scheduleName + ".NumEntr.setVal", null, "-1");
        disableOp = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO, "false");
        BasicDataAttribute enableOp2 = dut.findAndAssignValue(scheduleName + ".EnaReq.Oper.ctlVal", Fc.CO, "true");
        dut.operate((FcModelNode) disableOp.getParent().getParent());
        Executable executable2 = () -> dut.operate((FcModelNode) enableOp2.getParent().getParent());
        Assertions.assertThrows(ServiceError.class, executable2);

        //test: 200 can not bet set to NumEtr
        dut.setDataValues(scheduleName + ".NumEntr.setVal", null, "200");
        disableOp = dut.findAndAssignValue(scheduleName + ".DsaReq.Oper.ctlVal", Fc.CO, "false");
        BasicDataAttribute enableOp3 = dut.findAndAssignValue(scheduleName + ".EnaReq.Oper.ctlVal", Fc.CO, "true");
        dut.operate((FcModelNode) disableOp.getParent().getParent());
        Executable executable3 = () -> dut.operate((FcModelNode) enableOp3.getParent().getParent());
        Assertions.assertThrows(ServiceError.class, executable3);

    }

    @Requirements(S13)
    @ParameterizedTest(name = "reserveSchedulesCannotBeDeactivated running {0}")
    @MethodSource("getAllSchedules")
    public void reserveSchedulesCannotBeDeactivated(ScheduleDefinitions scheduleConstants)
            throws ServiceError, IOException {

        disableAllRunningSchedules();

        // if all other schedules are deactivated, the reserve schedule should be running
        final String reserveSchedule = scheduleConstants.getReserveSchedule();
        Assertions.assertEquals(ScheduleState.RUNNING, dut.getScheduleState(reserveSchedule));
        try {
            dut.disableSchedules(reserveSchedule);
        } catch (ServiceError e) {
            // an access violation may be thrown here, this indicates the schedule cannot be deactivated
        }
        Assertions.assertEquals(ScheduleState.RUNNING, dut.getScheduleState(reserveSchedule));
    }

    @ParameterizedTest(name = "reserveSchedulesHaveFixedPriority running {0}")
    @MethodSource("getAllSchedules")
    @Requirements(S14)
    public void reserveSchedulesHaveFixedPriority(ScheduleDefinitions scheduleConstants)
            throws ServiceError, IOException {
        final String reserveSchedule = scheduleConstants.getReserveSchedule();
        try {
            dut.setSchedulePrio(reserveSchedule, 100);
        } catch (ServiceError e) {
            // an access violation may be thrown here, this indicates the prio cannot be changed
        }
        Assertions.assertEquals(10, dut.readSchedulePrio(reserveSchedule));
    }

    @ParameterizedTest(name = "reserveSchedulesHaveFixedStart running {0}")
    @MethodSource("getAllSchedules")
    @Requirements(S15)
    public void reserveSchedulesHaveFixedStart(ScheduleDefinitions scheduleConstants) throws ServiceError, IOException {
        final String reserveSchedule = scheduleConstants.getReserveSchedule();
        Assertions.assertEquals(Instant.ofEpochSecond(1), dut.getScheduleStart(reserveSchedule));
        try {
            dut.setScheduleStart(reserveSchedule, Instant.now());
        } catch (ServiceError e) {
            // an access violation may be thrown here, this indicates the start date cannot be changed
        }
        Assertions.assertEquals(Instant.ofEpochSecond(1), dut.getScheduleStart(reserveSchedule));
    }

    /**
     * ############################### UTILITIES AND HELPERS ##################################
     */

    /**
     * Tests that a node is not present in the model. This is to facilitate testing as we can simply leave out nodes
     * that are not relevant for our use case.
     */
    private void testOptionalNodeNotPresent(ScheduleDefinitions scheduleConstants, String nodeName) {
        scheduleConstants.getAllScheduleNames().forEach(schedule -> {
            org.junit.jupiter.api.Assertions.assertFalse(dut.nodeExists(schedule + "." + nodeName),
                    "Optional node " + nodeName
                            + " not relevant for this use case, so it should be left out (behavior cannot be tested).");
        });
    }
}
