# Alice

This is a simple Android app used by the user to communicate with the AP (i.e. Raspberry Pi) in order to verify the user's location.

## Communication

Communication with the AP and server/verifier follows these steps (as described in the paper):

1. AP sends a ping to the user, containing the AP ID, `apid`</li>
2. User sends back the request (`req`), user id (`uid`), nonce (`unonce`), and AP ID (`apid`) back to the AP
3. AP verifies sequence ID sent by user and then sends user an acknowledgement containing Ack (*what is this??*), the user ID (`uid`), and the nonce (`unonce`)
4. User sends periodic packets to the AP</li>
5. AP generates location proof based on the packets and sends the proof to the user in the form of a `LocnProof'` protobuf (as defined in [API.md] (https://github.com/oxjhc/rabbits/blob/master/server/API.md#relevant-schemas))
6. User sends location proof to the server/verifier in order to verify the location claim</li>
7. Server/verifier sends a a confirmation/refusal of the location claim to the user in the form of a `Token'` protobuf (as defined in [API.md] (https://github.com/oxjhc/rabbits/blob/master/server/API.md#relevant-schemas))

Based on the token, the user knows whether or not they successfully proved their location.

## Use

The user presses the button on the screen in order to begin the sequence of communications with the AP and server/verified, as outlined above. The app will display on the screen whether or not the proof was successful.