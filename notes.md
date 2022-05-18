# Notes / Questions / Protocol


## Questions
- modeling of on/off commands:
  - What is it used or intended for?
  - IEC 61850 foresees INV1 (90-7, page 38ff) to connect and dissconnect DERs from the grid. Is that intended?
  ==> 18.5.: Connect/disconnect is intended. Modeling as a function not necessarily required, can also be done in a schedule. Preferably, the values in the schedule should be "ON" and "OFF" rather than "0" and "1".
- Function INV2: "adjust maximum generation level up/down" (90-7 38ff): can we just model this as a regular schedule?
  ==> 18.5.: yes, we can model maximum values with a dedicated schedule with explict naming.
