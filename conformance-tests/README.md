# Alliander rfg+ conformance tests

This project is aiming for testing the scheduling functions of an existing implementation against
the [requirements](../REQUIREMENTS.md).

To link the [tests](main/src/test/java/org/openmuc/fnn/steuerbox/)
to their requirements, the requirements are being copied
into [a java class](src/main/java/org/openmuc/fnn/steuerbox/models/Requirement.java)
such that they can be referenced in the javadoc of the tests. A java IDE (
e.g. [intelliJ](https://www.jetbrains.com/idea/)) will render this in a way that references can be followed
bidirectionally.

## License

This test framework is licensed under Apache 2.0 license. It uses dependencies licensed under

- Eclipse Public license 1.0
- MIT license
- Apache 2.0 license 

see [NOTICE](NOTICE) for details.

## Running the tests

The tests will later on run with JUnit, a common testing framework for java. They can be started by
calling `./gradlew test` for linux systems or `gradlew.bat test` in Windows.

# Test setup

Tested runtime environment is openjdk 11. The test assumes the test device is running on localhost (127.0.0.1) and
accessible on port 102. [This is currently hard coded.](test/tests/src/main/java/org/openmuc/fnn/steuerbox/models/AllianderDER.java#L17)

## Getting started

For a first impression, it probably makes sense to have a look into the  [requirements](src/main/java/org/openmuc/fnn/steuerbox/models/Requirement.java)
and the general [IEC 61850 scheduling execution tests](tests/src/test/java/org/openmuc/fnn/steuerbox/ScheduleExecutionTest.java)
, tests on existing IEC 61850
nodes [of schedule controller](src/test/java/org/openmuc/fnn/steuerbox/ScheduleControllerNodeTests.java)
and [schedule](src/test/java/org/openmuc/fnn/steuerbox/ScheduleNodeTests.java) and more
[Alliander spcific tests](main/src/test/java/org/openmuc/fnn/steuerbox/AllianderTests.java)
.
