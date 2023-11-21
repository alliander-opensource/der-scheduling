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

import com.beanit.iec61850bean.Fc;
import com.beanit.iec61850bean.FcModelNode;
import com.beanit.iec61850bean.ModelNode;
import com.beanit.iec61850bean.ObjectReference;
import com.beanit.iec61850bean.ServiceError;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openmuc.fnn.steuerbox.models.AllianderDER;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleDefinitions;
import org.openmuc.fnn.steuerbox.scheduling.ScheduleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Common fields and methods for 61850 testing
 */
public abstract class AllianderBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleNodeTests.class);

    protected static AllianderDER dut;

    @BeforeAll
    public static void connectToDUT() throws ServiceError, IOException {
        dut = AllianderDER.getWithDefaultSettings();
    }

    @BeforeEach
    public  void stopAllRunningSchedules(){
        disableAllRunningSchedules();
        logger.debug("Disabled all schedules during init");
    }

    @AfterAll
    public static void shutdownConnection() {
        dut.close();
    }

    public static void disableAllRunningSchedules() {
        getAllSchedules().forEach(scheduleType -> {
            scheduleType.getAllScheduleNames().forEach(schedule -> {
                try {
                    dut.disableSchedules(schedule);
                } catch (Exception e) {
                    Assertions.fail("error, could not disable schedule " + schedule);
                    logger.error("error, could not disable schedule " + schedule, e);
                }
            });
        });
    }

    /**
     * These schedules use float values for scheduling
     */
    protected static Stream<ScheduleDefinitions<?>> getPowerValueSchedules() {
        return getAllSchedules().filter(s -> ScheduleType.ASG == s.getScheduleType());
    }

    /**
     * These schedules use boolean values for scheduling
     */
    protected static Stream<ScheduleDefinitions<?>> getOnOffSchedules() {
        return getAllSchedules().filter(s -> ScheduleType.SPG == s.getScheduleType());
    }

    protected static Stream<ScheduleDefinitions<?>> getAllSchedules() {
        return Stream.of(dut.powerSchedules, dut.maxPowerSchedules, dut.onOffSchedules);
    }

    public static void assertValuesMatch(List<Float> expectedValues, List<Float> actualValues, double withPercentage) {
        Assertions.assertEquals(actualValues.size(), expectedValues.size());
        for (int i = 0; i < expectedValues.size(); i++) {
            Float expected = expectedValues.get(i);
            Float actual = actualValues.get(i);
            Assertions.assertEquals(expected, actual,withPercentage,"Array does not match at index "+i+". "
                    + "\nExpected values: "+expectedValues+""
                    + "\nactual values  : "+actualValues+"\n");
        }
    }

    private static boolean areClose(Float actual, Float expected, double withPercentage) {
        double lowerBound = expected * (1.0 - withPercentage / 100f);
        double upperBound = expected * (1.0 + withPercentage / 100f);
        return actual >= lowerBound && actual <= upperBound;
    }

    public static void assertValuesMatch(List<Boolean> expectedValues, List<Boolean> actualValues) {
        Assertions.assertEquals(actualValues.size(), expectedValues.size());
        for (int i = 0; i < expectedValues.size(); i++) {
            boolean expected = expectedValues.get(i);
            boolean actual = actualValues.get(i);
            Assertions.assertEquals(expected, actual,"Array does not match at index "+i+". "
                    + "\nExpected values: "+expectedValues+""
                    + "\nactual values  : "+actualValues+"\n");
        }
    }


    protected void assertUntypedValuesMatch(List<?> expectedValues, List<?> actualValues) {
        if(expectedValues.size() <= 0){
            if(expectedValues.size() == 0 && actualValues.size()==0){

            }
            else {
                Assertions.fail("Values do not match, different array sizes. Expected "+expectedValues.size()+" but got "+actualValues.size());
            }
        }
        if (Float.class.equals(expectedValues.get(0).getClass())) {
            assertValuesMatch((List<Float>) expectedValues, (List<Float>) actualValues,0.1);
        }
        else if (Boolean.class.equals(expectedValues.get(0).getClass())) {
            assertValuesMatch((List<Boolean>) expectedValues, (List<Boolean>) actualValues);
        }else {
            throw new IllegalArgumentException("Expected Float or Boolean, got:"+expectedValues.get(0).getClass());
        }
    }

    protected Collection<String> testMandatoryNodes(Map<String, Fc> mandatory, String parentNode) {
        Collection<String> violations = new LinkedList<>();

        for (Map.Entry<String, Fc> entry : mandatory.entrySet()) {
            String fullNodeName = parentNode + "." + entry.getKey();

            if (dut.nodeExists(fullNodeName)) {
                ModelNode node = dut.getNode(fullNodeName);

                Fc actualFc = ((FcModelNode) node).getFc();
                if (!Objects.equals(entry.getValue(), actualFc)) {
                    violations.add("Mandatory requirement: node " + entry.getKey() + " is not the expected type "
                            + entry.getValue() + " but instead " + actualFc);
                }
            }
            else {
                violations.add("Mandatory requirement: node" + fullNodeName + "is not present");
            }
        }
        return violations;
    }

    protected Collection<String> testAtMostOnceNodes(Map<String, Fc> atMostOne, String parentNode) {

        Collection<String> violations = new LinkedList<>();

        for (Map.Entry<String, Fc> entry : atMostOne.entrySet()) {
            ModelNode scheduleNode = dut.getNode(parentNode);
            if (scheduleNode == null) {
                Assertions.fail("Unable to find node " + parentNode);
            }
            List<String> occurencesThatContainKeyInName = scheduleNode.getChildren().stream()//
                    .filter(childNode -> childNode.getName().contains(entry.getKey()))//
                    .map(ModelNode::getReference)//
                    .map(ObjectReference::toString)// in order for distinct to work, ObjectReference does not implement equals() nor hashCode()
                    .distinct()//
                    .collect(Collectors.toList());

            if (occurencesThatContainKeyInName.size() > 1) {
                violations.add("atMostOnce requirement: Expected at most 1 occurence of nodes with '" + entry.getKey()
                        + "' in their name but found" + occurencesThatContainKeyInName.size() + ": "
                        + occurencesThatContainKeyInName);
            }
        }
        return violations;
    }

    protected Collection<String> testOMulti(Map<String, Fc> oMulti, String parentNode) {
        Collection<String> violations = new LinkedList<>();

        for (Map.Entry<String, Fc> entry : oMulti.entrySet()) {
            List<ModelNode> occurencesThatContainKeyInName = dut.getNode(parentNode)
                    .getChildren()
                    .stream()//
                    .filter(childNode -> childNode.getName().contains(entry.getKey()))//
                    .filter(childNode -> entry.getKey().equals(removeNumbers(childNode.getName())))
                    .collect(Collectors.toList());

            for (ModelNode foundOptionalNode : occurencesThatContainKeyInName) {
                Fc actualFc = ((FcModelNode) foundOptionalNode).getFc();

                if (!Objects.equals(entry.getValue(), actualFc)) {
                    violations.add("Omulti requirement: Node " + foundOptionalNode.getReference()
                            + " does not have expected type " + entry.getValue() + " but instead " + actualFc);
                }

                Optional<Integer> number = extractNumberFromLastNodeName(foundOptionalNode.getName());
                if (number.isEmpty()) {
                    violations.add(
                            "Omulti requirement: Expected a instance number in node " + foundOptionalNode.getReference()
                                    + " but found none.");
                }
                else if (number.get() < 1) {
                    violations.add("Omulti requirement: Expected a instance number larger than 0 but found " + number
                            + " in Omulti node " + foundOptionalNode.getReference());
                }
            }
        }
        return violations;
    }

    public static String removeNumbers(String stringWithNumbers) {
        if (stringWithNumbers == null) {
            return null;
        }
        return stringWithNumbers.replaceAll("[0-9]", "");
    }

    protected Collection<String> testMMulti(Map<String, Fc> mMulti, String parentNode) {
        Collection<String> violations = new LinkedList<>();

        for (Map.Entry<String, Fc> entry : mMulti.entrySet()) {
            List<FcModelNode> occurencesThatContainKeyInName = dut.getNode(parentNode)
                    .getChildren()
                    .stream()//
                    .filter(childNode -> childNode.getName().contains(entry.getKey()))//
                    .filter(childNode -> childNode instanceof FcModelNode)
                    .map(childNode -> (FcModelNode) childNode)
                    .collect(Collectors.toList());

            if (occurencesThatContainKeyInName.isEmpty()) {
                violations.add("Mmulti requirement: Expected a node that contains " + entry.getKey()
                        + " in its name but found none for parent " + parentNode);
                continue; // just to make this clear, the further for loop will not be executed anyways...
            }

            for (ModelNode foundOptionalNode : occurencesThatContainKeyInName) {
                Fc actualFc = ((FcModelNode) foundOptionalNode).getFc();
                if (!Objects.equals(entry.getValue(), actualFc)) {
                    violations.add("Mmulti requirement: Node " + foundOptionalNode.getReference()
                            + " does not have expected type " + entry.getValue() + " but instead " + actualFc);
                }

                Optional<Integer> number = extractNumberFromLastNodeName(foundOptionalNode.getName());
                if (number.isEmpty()) {
                    violations.add(
                            "Omulti requirement: Expected a instance number in node " + foundOptionalNode.getReference()
                                    + " but found none.");
                }
                else if (number.get() < 1) {
                    violations.add("Omulti requirement: Expected a instance number larger than 0 but found " + number
                            + " in Omulti node " + foundOptionalNode.getReference());
                }
            }
        }
        return violations;
    }

    public static Optional<Integer> extractNumberFromLastNodeName(String foundNodeName) {
        try {
            String[] split = foundNodeName.split("\\.");

            String lastElement = foundNodeName;
            if (split.length > 0) {
                lastElement = split[split.length - 1];
            }
            String numberAsString = lastElement.replaceAll("[^0-9]", "");
            return Optional.of(Integer.parseInt(numberAsString));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    protected Collection<String> testMMultiF(Collection<AllianderTests.MandatoryOnCondition> mMultiF,
            String parentNode) {
        Collection<String> violations = new LinkedList<>();

        for (AllianderTests.MandatoryOnCondition mandatoryOnCondition : mMultiF) {
            String conditionNode = parentNode + mandatoryOnCondition.condition;
            if (dut.nodeExists(conditionNode)) {

                String nowMandatoryNode = parentNode + mandatoryOnCondition.mandatoryOnCondition;
                if (!dut.nodeExists(nowMandatoryNode)) {
                    violations.add("Node " + nowMandatoryNode + " is not present though it is required because node "
                            + conditionNode + " is present");
                }
            }
            else {
                // condition not met, nothing more to check
            }
        }
        return violations;
    }

    protected Collection<String> testOptionalNodes(Map<String, Fc> optional, String parentNode) {
        Collection<String> violations = new LinkedList<>();
        for (Map.Entry<String, Fc> entry : optional.entrySet()) {
            String fullNodeName = parentNode + "." + entry.getKey();

            if (dut.nodeExists(fullNodeName)) {
                ModelNode node = dut.getNode(fullNodeName);

                Fc actualFc = ((FcModelNode) node).getFc();
                Fc expectedFc = entry.getValue();
                if (!Objects.equals(expectedFc, actualFc)) {
                    violations.add("Optional node " + fullNodeName + " does not have expected type " + expectedFc
                            + " but instead " + actualFc);
                }
            }
            else {
                // optional node is not present -> nothing more to do
            }
        }
        return violations;
    }

    protected static class MandatoryOnCondition {
        final String condition;
        final String mandatoryOnCondition;
        final Fc fc;

        interface SpecifiedCondition {
            MandatoryOnCondition thenMandatory(String nodeName, Fc fc);
        }

        public static SpecifiedCondition ifPresent(String nodeName) {
            return (thenMandatoryNode, fc) -> new MandatoryOnCondition(nodeName, thenMandatoryNode, fc);
        }

        private MandatoryOnCondition(String condition, String mandatoryOnCondition, Fc fc) {
            this.condition = condition;
            this.mandatoryOnCondition = mandatoryOnCondition;
            this.fc = fc;
        }
    }
}
