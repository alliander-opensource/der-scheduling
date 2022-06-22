# REQUIREMENTS

Basic schedule funtionality shall be supported, as depicted below:

![Scheduling-components-overview](images/61850-90-7_6.3.2_Schedules_relevant_parts.png)

# Configuration

[C01]: Basic configuration of DER default shall be stored inside a SCL File

[C01-a]: reserve schedules shall be stored inside a SCL File

[C01-b]: number of available schedules (maximum 10 schedules per logical Node, less can be configured) shall be stored inside a SCL File

[C01-c]: number of devices to be controlled (limited to 1 for evaluation, extension shall be foreseen) shall be stored inside a SCL File

[C02]: Parameters of schedules other than the reserve schedule are to be stored in a key-value manner

[C03]: Parameters of schedules other than the reserve schedule are persisted to a separate file (e.g. as JSON)

[C04]: Changes in schedules are persisted into the file as soon as the schedule is validated

[C05]: A interface to CoMPAS is not required.

[C05-a]: A simple interface to notify about changes in SCL file is to be defined.

[C05-b]: A simple interface to notify about changes in key-value

# Logical Nodes
[LN01]: Map Logical Nodes and Common Data Classes to configuration values, allow reading and writing according to 61850-90-10

[LN02]: The control functions of the DER are modeled and implemented independently

[LN02-a]: Absolute active power is controlled in 10 logical nodes ActPow_FSCH01 .. ActPow_FSCH10, with schedule controller ActPow_FSCC and actuator interface ActPow_GGIO

[LN02-b]: Maximum power is controlled in 10 logical nodes WGn_FSCH01 .. WGn_FSCH10, with schedule controller WGn_FSCC and actuator interface WGn_GGIO

[LN02-b]: On/Off state of the DER is controlled in 10 logical nodes for the schedules, with schedule controller and an actuator interface. The names are to be defined.

[LN03]: The scheduling nodes and their children are to be modeled according to 61850-90-10 

# Scheduling
[S01]: A set of 10 schedules per logical node must be supported

[S02]: Support time based schedules

[S03]: Triggers other than time based may be supported

[S04]: Periodical schedules may be supported

[S05a]: Support of absolute schedules of absolute power setpoints

[S05b]: Support of absolute schedules of maximum power setpoints

[S05c]: Support of schedules to turn DER on and off

[S08]: Validation of Schedules according to 61850-90-10

[S09]: Schedules for maximum generation values and absolute power values are modeled in the same way (using the same DTO)

[S10]: Each schedule must store a sequence of max. 100 values

# Schedule execution
[E01]: Execution of schedules must regard schedule priority according to 61850-10

[E02]: Resolution time base: >= 1 second
