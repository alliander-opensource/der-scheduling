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

import com.beanit.iec61850bean.ServiceError;
import de.fhg.ise.testtool.utils.annotations.label.Description;
import de.fhg.ise.testtool.utils.annotations.label.Requirements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openmuc.fnn.steuerbox.scheduling.PreparedSchedule;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.time.Instant.now;
import static org.openmuc.fnn.steuerbox.models.Requirement.E01;
import static org.openmuc.fnn.steuerbox.models.Requirement.E02;
import static org.openmuc.fnn.steuerbox.models.Requirement.LN01;
import static org.openmuc.fnn.steuerbox.models.Requirement.S02;
import static org.openmuc.fnn.steuerbox.models.Requirement.S05c;
import static org.openmuc.fnn.steuerbox.models.Requirement.S09;
import static org.openmuc.fnn.steuerbox.models.Requirement.S17;

/**
 * Holds tests related to 61850 schedule execution
 */
public class ScheduleExecutionTest extends AllianderBaseTest {

    private static final Logger log = LoggerFactory.getLogger(ScheduleExecutionTest.class);

    @BeforeAll
    public static void setDefaultValuesForReserveSchedules() throws ServiceError, IOException {
        dut.writeScheduleValues(
                dut.powerSchedules.getValueAccess().prepareWriting(0, dut.powerSchedules.getReserveSchedule()));
        dut.writeScheduleValues(
                dut.maxPowerSchedules.getValueAccess().prepareWriting(0, dut.maxPowerSchedules.getReserveSchedule()));
        dut.writeScheduleValues(
                dut.onOffSchedules.getValueAccess().prepareWriting(false, dut.onOffSchedules.getReserveSchedule()));
        log.info("Set default values for reserve schedules");
    }

    @DisplayName("test_prioritiesPowerSchedules")
    @Requirements({ E02, S02, S05c, E01, LN01, S09 })
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getPowerValueSchedules")
    public void test_prioritiesPowerSchedules(ScheduleDefinitions<Number> scheduleConstants)
            throws ServiceError, IOException, InterruptedException, IEC61850MissconfiguredException {

        // do not change the interval, this is demanded by a requirement!
        final Duration interval = ofSeconds(1);

        final Instant testExecutionStart = now();
        final Instant schedulesStart = testExecutionStart.plus(ofSeconds(10));

        //schedule 1:
        dut.writeAndEnableSchedule(
                scheduleConstants.prepareSchedule(Arrays.asList(10, 30, 70, 100, 100, 100), 1, interval,
                        schedulesStart.plus(interval), 25));

        // schedule 2:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(11, 31, 71, 99, 99, 99), 2, interval,
                schedulesStart.plus(interval.multipliedBy(5)), 40));

        // schedule 3:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(12, 32, 72, 98, 98), 3, interval,
                schedulesStart.plus(interval.multipliedBy(9)), 60));

        //schedule 4, ends after 44s:
        dut.writeAndEnableSchedule(
                scheduleConstants.prepareSchedule(Arrays.asList(13, 33, 73, 97, 97, 97, 97, 97, 97, 97), 4, interval,
                        schedulesStart.plus(interval.multipliedBy(13)), 70));

        //schedule 5, ends after 42s
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(70, 70, 70, 70, 70), 5, interval,
                schedulesStart.plus(interval.multipliedBy(17)), 100));

        //schedule 6,
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(90), 6, interval,
                schedulesStart.plus(interval.multipliedBy(18)), 120));

        //schedule 7,

        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(90), 7, interval,
                schedulesStart.plus(interval.multipliedBy(20)), 120));

        //schedule 8:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(10), 8, interval,
                schedulesStart.plus(interval.multipliedBy(22)), 80));

        //schedule 9
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(80), 9, interval,
                schedulesStart.plus(interval.multipliedBy(23)), 20));

        //schedule 10
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(100), 10, interval,
                schedulesStart.plus(interval.multipliedBy(24)), 11));

        log.debug("Test setup took {}", Duration.between(testExecutionStart, now()));

        float sysResValue = dut.readConstantValueFromSysResScheduleFromModelNode(scheduleConstants.getValueAccess(),
                scheduleConstants.getReserveSchedule()).floatValue();

        List<Float> expectedValues = Arrays.asList(sysResValue, 10f, 30f, 70f, 100f, 11f, 31f, 71f, 99f, 12f, 32f, 72f,
                98f, 13f, 33f, 73f, 97f, 70f, 90f, 70f, 90f, 70f, 10f, 80f, 100f, sysResValue);

        List<Float> actualValues = dut.monitor(schedulesStart.plus(interval.dividedBy(2)), interval.multipliedBy(26),
                interval, scheduleConstants);

        log.info("expected values {}", expectedValues);
        log.info("observed values {}", actualValues);

        assertValuesMatch(expectedValues, actualValues, 0.01);
    }

    @DisplayName("test_prioritiesOnOffSchedules")
    @Requirements({ E02, S02, S05c, E01 })
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getOnOffSchedules")
    public void test_prioritiesOnOffSchedules(ScheduleDefinitions<Boolean> scheduleConstants)
            throws ServiceError, IOException, IEC61850MissconfiguredException, InterruptedException {

        // do not change the interval, this is demanded by a requirement!
        final Duration interval = ofSeconds(1);

        final Instant testExecutionStart = now();
        final Instant schedulesStart = testExecutionStart.plus(ofSeconds(5));

        boolean sysResValue = dut.readConstantValueFromSysResScheduleFromModelNode(scheduleConstants.getValueAccess(),
                scheduleConstants.getReserveSchedule());

        log.debug("Test setup took {}", Duration.between(testExecutionStart, now()));

        boolean s1 = false;
        boolean s2 = true;
        boolean s3 = false;
        boolean s4 = true;
        boolean s5 = false;
        boolean s6 = true;
        boolean s7 = false;
        boolean s8 = true;
        boolean s9 = false;
        boolean s10 = true;

        // setting up schedules with increasing priorities, starting one after the other. exception: schedule 10 (is set up last)

        //schedule 1:
        dut.writeAndEnableSchedule(
                scheduleConstants.prepareSchedule(Arrays.asList(s1, s1, s1), 1, interval, schedulesStart.plus(interval),
                        15));
        //schedule 2:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s2, s2, s2), 2, interval,
                schedulesStart.plus(interval.multipliedBy(2)), 20));
        //schedule 3:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s3, s3), 3, interval,
                schedulesStart.plus(interval.multipliedBy(3)), 30));
        //schedule 4:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s4, s4), 4, interval,
                schedulesStart.plus(interval.multipliedBy(4)), 40));
        //schedule 5:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s5, s5), 5, interval,
                schedulesStart.plus(interval.multipliedBy(5)), 50));
        //schedule 6:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s6, s6), 6, interval,
                schedulesStart.plus(interval.multipliedBy(6)), 60));
        //schedule 7:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s7, s7), 7, interval,
                schedulesStart.plus(interval.multipliedBy(7)), 70));
        //schedule 8:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s8, s8), 8, interval,
                schedulesStart.plus(interval.multipliedBy(8)), 80));
        //schedule 9:
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s9), 9, interval,
                schedulesStart.plus(interval.multipliedBy(9)), 90));
        //schedule 10: starts together with schedule 9 but with lower priority. lasts longer, so is activated after schedule 9 is stopped
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(s10, s10), 10, interval,
                schedulesStart.plus(interval.multipliedBy(9)), 11));

        List<Boolean> expectedValues = Arrays.asList(sysResValue, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, sysResValue);
        List<Boolean> actualValues = dut.monitor(schedulesStart.plus(interval.dividedBy(2)), interval.multipliedBy(12),
                interval, scheduleConstants);

        log.info("expected values {}", expectedValues);
        log.info("observed values {}", actualValues);

        assertValuesMatch(expectedValues, actualValues);
    }

    /**
     * concerning IEC61850 a schedule with the same prio but later start time rules out the one with this prio but
     * earlier start time, test for float schedules
     */
    @DisplayName("testSamePriosDifferentStartPowerSchedules")
    @Description("IEC61850-90-10 ed 2017 Schedule Controller Definitions, section 5.5.3")
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getPowerValueSchedules")
    public void testSamePriosDifferentStartPowerSchedules(ScheduleDefinitions scheduleConstants)
            throws ServiceError, IOException, InterruptedException, IEC61850MissconfiguredException {

        final Instant testExecutionStart = now().plus(ofSeconds(10));
        final Instant schedulesStart = testExecutionStart.plus(ofSeconds(4));

        //schedule 5, start after 2s, duration 12s, Prio 40
        dut.writeAndEnableSchedule(
                scheduleConstants.prepareSchedule(Arrays.asList(10, 10, 10, 10, 10, 10), 5, ofSeconds(4),
                        schedulesStart, 40));
        //schedule 1, start after 4s, duration 4s, Prio 40
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(70, 100), 1, ofSeconds(4),
                schedulesStart.plus(ofSeconds(4)), 40));
        //schedule 2, start after 8s, duration 4s, Prio 40
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(70, 70), 2, ofSeconds(4),
                schedulesStart.plus(ofSeconds(12)), 40));
        //schedule 3, start after 14s, duration 2s, Prio 60
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(100), 3, ofSeconds(4),
                schedulesStart.plus(ofSeconds(24)), 60));

        float sysResValue = (float) dut.readConstantValueFromSysResScheduleFromModelNode(
                scheduleConstants.getValueAccess(), scheduleConstants.getReserveSchedule());
        List<Float> expectedValues = Arrays.asList(sysResValue, 10f, 70f, 100f, 70f, 70f, 10f, 100f, sysResValue);

        Instant monitoringStart = testExecutionStart.plus(ofSeconds(2));
        List<Float> actualValues = dut.monitor(monitoringStart, ofSeconds(36), ofSeconds(4), scheduleConstants);
        log.info("expected values {}", expectedValues);
        log.info("observed values {}", actualValues);
        assertValuesMatch(expectedValues, actualValues, 0.01);
    }

    /**
     * concerning IEC61850 a schedule with the same prio but later start time rules out the one with this prio but
     * earlier start time, test for boolean schedules
     */
    @DisplayName("testSamePriosDifferentStartOnOffSchedule")
    @Description("IEC61850-90-10 ed 2017 Schedule Controller Definitions, section 5.5.3")
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getOnOffSchedules")
    public void testSamePriosDifferentStartOnOffSchedule(ScheduleDefinitions scheduleConstants)
            throws ServiceError, IOException, InterruptedException, IEC61850MissconfiguredException {

        final Instant testExecutionStart = now();
        final Instant schedulesStart = testExecutionStart.plus(ofSeconds(2));

        //schedule 1, start after 2s, duration 6s, Prio 40
        dut.writeAndEnableSchedule(
                scheduleConstants.prepareSchedule(Arrays.asList(true, true, true), 1, ofSeconds(2), schedulesStart,
                        40));
        //schedule 2, start after 4s, duration 4s, Prio 40
        dut.writeAndEnableSchedule(scheduleConstants.prepareSchedule(Arrays.asList(false, false), 2, ofSeconds(2),
                schedulesStart.plus(ofSeconds(2)), 40));

        boolean sysResValue = (boolean) dut.readConstantValueFromSysResScheduleFromModelNode(
                scheduleConstants.getValueAccess(), scheduleConstants.getReserveSchedule());
        List<Boolean> expectedValues = Arrays.asList(sysResValue, true, false, false, sysResValue);

        Instant monitoringStart = testExecutionStart.plus(ofSeconds(1));
        List<Boolean> actualValues = dut.monitor(monitoringStart, ofSeconds(10), ofSeconds(2), scheduleConstants);
        log.info("expected values {}", expectedValues);
        log.info("observed values {}", actualValues);
        assertValuesMatch(expectedValues, actualValues);
    }

    /**
     * two schedules with the same prio and start: the one with the lower Number in its name rules out the other one,
     * e.g. OnOffPow_FSCH04 rules put OnOffPow_FSCH10 IEC61850 does not determine a certain behavior in this case, this
     * is just a detail that was fixed for implementation
     */
    @DisplayName("test_samePrioAndStartFloatSchedule")
    @Requirements({ S17 })
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getPowerValueSchedules")
    public void test_samePrioAndStartFloatSchedule(ScheduleDefinitions<Number> scheduleConstants)
            throws ServiceError, IOException, IEC61850MissconfiguredException, InterruptedException {

        float sysResValue = (float) dut.readConstantValueFromSysResScheduleFromModelNode(
                scheduleConstants.getValueAccess(), scheduleConstants.getReserveSchedule());

        Instant start = Instant.now();
        PreparedSchedule schedule1 = scheduleConstants.prepareSchedule(Arrays.asList(70f, 70f), 1, ofSeconds(3),
                start.plus(ofSeconds(3)), 40);
        PreparedSchedule schedule2 = scheduleConstants.prepareSchedule(Arrays.asList(100f, 100f, 100f), 2, ofSeconds(3),
                start.plus(ofSeconds(3)), 40);

        dut.writeAndEnableSchedule(schedule1);
        dut.writeAndEnableSchedule(schedule2);

        List<Float> expectedValues = Arrays.asList(sysResValue, 70f, 70f, 100f, sysResValue);
        List<Float> actualValues = dut.monitor(start.plus(ofMillis(1000)), ofSeconds(15), ofSeconds(3),
                scheduleConstants);
        assertValuesMatch(expectedValues, actualValues, 0.01);
    }

    /**
     * two schedules with the same prio and start: the one with the lower Number in its name rules out the other one,
     * e.g. OnOffPow_FSCH04 rules put OnOffPow_FSCH10 IEC61850 does not determine a certain behavior in this case, this
     * is just a detail that was fixed for implementation
     */
    @DisplayName("test_samePrioAndStartOnOffSchedule")
    @Requirements({ S17 })
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getOnOffSchedules")
    public void test_samePrioAndStartOnOffSchedule(ScheduleDefinitions<Boolean> scheduleConstants)
            throws ServiceError, IOException, IEC61850MissconfiguredException, InterruptedException {
        boolean sysResValue = dut.readConstantValueFromSysResScheduleFromModelNode(scheduleConstants.getValueAccess(),
                scheduleConstants.getReserveSchedule());

        Instant start = Instant.now();
        PreparedSchedule schedule1 = scheduleConstants.prepareSchedule(Arrays.asList(true, true), 1, ofSeconds(3),
                start.plus(ofSeconds(3)), 40);
        PreparedSchedule schedule2 = scheduleConstants.prepareSchedule(Arrays.asList(false, false, false), 2,
                ofSeconds(3), start.plus(ofSeconds(3)), 40);

        dut.writeAndEnableSchedule(schedule1);
        dut.writeAndEnableSchedule(schedule2);

        List<Boolean> expectedValues = Arrays.asList(sysResValue, true, true, false, sysResValue);
        List<Boolean> actualValues = dut.monitor(start.plus(ofMillis(1000)), ofSeconds(15), ofSeconds(3),
                scheduleConstants);
        assertValuesMatch(expectedValues, actualValues);
    }
}
