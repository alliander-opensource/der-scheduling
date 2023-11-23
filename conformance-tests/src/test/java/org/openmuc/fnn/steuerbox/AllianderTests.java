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

import de.fhg.ise.testtool.utils.annotations.label.Requirements;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleDefinitions;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.openmuc.fnn.steuerbox.models.Requirement.LN02a;
import static org.openmuc.fnn.steuerbox.models.Requirement.LN02b;
import static org.openmuc.fnn.steuerbox.models.Requirement.LN02c;
import static org.openmuc.fnn.steuerbox.models.Requirement.S01;
import static org.openmuc.fnn.steuerbox.models.Requirement.S02;
import static org.openmuc.fnn.steuerbox.models.Requirement.S04;
import static org.openmuc.fnn.steuerbox.models.Requirement.S05a;
import static org.openmuc.fnn.steuerbox.models.Requirement.S05b;
import static org.openmuc.fnn.steuerbox.models.Requirement.S05c;
import static org.openmuc.fnn.steuerbox.models.Requirement.S10;
import static org.openmuc.fnn.steuerbox.models.Requirement.S11;
import static org.openmuc.fnn.steuerbox.models.Requirement.S12;

/**
 * Tests suiting the requirements in https://github.com/alliander-opensource/der-scheduling/blob/main/REQUIREMENTS.md
 * <p>
 * Systemoverview available at https://github.com/alliander-opensource/der-scheduling/blob/main/images/system-overview.drawio.png
 * <p>
 * General tests related to 61850 are to be found in {@link ScheduleExecutionTest}
 */
public class AllianderTests extends AllianderBaseTest {

    private static final Logger log = LoggerFactory.getLogger(AllianderTests.class);

    @DisplayName("tenSchedulesAreSupportedPerType")
    @Requirements(S01)
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getAllSchedules")
    void tenSchedulesAreSupportedPerType(ScheduleDefinitions<?> scheduleConstants) {
        int j = 0;
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {
            boolean ScheduleExistsOrNot = dut.nodeExists(scheduleName);
            Assertions.assertTrue(ScheduleExistsOrNot, "Schedule " + scheduleName + " does not exist");
            if (ScheduleExistsOrNot)
                j++;
        }
        log.info("There are {} existing schedules at this logical node", j);
    }

    @DisplayName("scheduleSupports100values")
    @Requirements(S10)
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getAllSchedules")
    void scheduleSupports100values(ScheduleDefinitions<?> scheduleConstants) {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {
            assert100ScheduleValuesAreSupported(scheduleConstants, scheduleName);
        }
    }

    @DisplayName("reserveScheduleSupports100values")
    @Requirements(S12)
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getAllSchedules")
    void reserveScheduleSupports100values(ScheduleDefinitions<?> scheduleConstants) {
        assert100ScheduleValuesAreSupported(scheduleConstants, scheduleConstants.getReserveSchedule());
    }

    private void assert100ScheduleValuesAreSupported(ScheduleDefinitions<?> scheduleConstants, String scheduleName) {
        final String valNodeName;
        if (scheduleConstants.getScheduleType() == ScheduleType.SPG)
            valNodeName = ".ValSPG";
        else
            valNodeName = ".ValASG";
        for (int i = 1; i <= 100; i++) {
            String numberAsStringFilledUpWithZeros = String.format("%03d", i);
            String valueConfigurationNode = scheduleName + valNodeName + numberAsStringFilledUpWithZeros;
            boolean valueExists = dut.nodeExists(valueConfigurationNode);
            Assertions.assertTrue(valueExists, "Missing node " + valueConfigurationNode);
        }
    }

    @DisplayName("scheduleSupportsTimebasedScheduling")
    @Requirements(S02)
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getAllSchedules")
    void scheduleSupportsTimebasedScheduling(ScheduleDefinitions scheduleConstants) {
        for (int i = 1; i <= 10; i++) {
            String aTimerNode = scheduleConstants.getScheduleName(i) + ".StrTm01";
            Assertions.assertTrue(dut.nodeExists(aTimerNode),
                    "Node " + aTimerNode + " does not exist. Time based scheduling is not possible without this node");
        }
    }

    @DisplayName("allExpectedSchedulesExist")
    @Requirements({ LN02a, LN02b, LN02c })
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getAllSchedules")
    void allExpectedSchedulesExist(ScheduleDefinitions<?> scheduleConstants) {
        for (String scheduleName : scheduleConstants.getAllScheduleNames()) {
            Assertions.assertTrue(dut.nodeExists(scheduleName),
                    "Expected schedule " + scheduleName + " to exist but did not find it");
        }
    }

    @DisplayName("reserveSchedulesExist")
    @Requirements(S04)
    @ParameterizedTest(name = " running {0}")
    @MethodSource("getAllSchedules")
    void reserveSchedulesExist(ScheduleDefinitions scheduleConstants) {
        Assertions.assertTrue(dut.nodeExists(scheduleConstants.getReserveSchedule()));
    }

    @Requirements(S05a)
    @Test
    void absolutePowerValueSchedulesAreSupported() {
        for (String absolutePowerValueSchedule : dut.powerSchedules.getAllScheduleNames()) {
            Assertions.assertTrue(dut.nodeExists(absolutePowerValueSchedule));
        }
    }

    @Requirements(S05b)
    @Test
    void maxPowerValueSchedulesAreSupported() {
        for (String absolutePowerValueSchedule : dut.maxPowerSchedules.getAllScheduleNames()) {
            Assertions.assertTrue(dut.nodeExists(absolutePowerValueSchedule));
        }
    }

    @Requirements(S05c)
    @Test
    void onOffSchedulesAreSupported() {
        for (String absolutePowerValueSchedule : dut.onOffSchedules.getAllScheduleNames()) {
            Assertions.assertTrue(dut.nodeExists(absolutePowerValueSchedule));
        }
    }

    @Test
    @Requirements(S11)
    void threeReserveSchedulesExist() {
        Assertions.assertTrue(dut.nodeExists(dut.onOffSchedules.getReserveSchedule()));
        Assertions.assertTrue(dut.nodeExists(dut.maxPowerSchedules.getReserveSchedule()));
        Assertions.assertTrue(dut.nodeExists(dut.powerSchedules.getReserveSchedule()));
    }

}
