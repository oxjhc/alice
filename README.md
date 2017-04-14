# Alice

This is a simple Android app used by the user to communicate with the AP (i.e. Raspberry Pi) and verifier in order to verify the user's location.

## Communication

Communication between the user and the AP/verifier follows the steps below, as described in the paper. Each step is annotated with the message number/name that is specified in the paper in order for easier cross-referencing.

1. [beacon] AP sends a UDP ping packet to the user on port 1832 (the birthyear of Lewis Carroll!), containing the sequence identifier, `seqid`
2. [m1] User sends back a `SignedProofReq` protobuf to the AP, which contains the user ID `uid`, user nonce `unonce`, sequence ID `seqid`, and verifier ID `vid`
3. [m2] AP verifies that the sequence ID sent by user matches the one the AP sent in its ping and then sends a `SignedProofResp` protobuf to the user, which contains the user ID `uid` and the user nonce `unonce`
4. User sends periodic packets to the AP via the phone's hotspot
5. [Locn_proof] AP generates location proof based on the packets and sends a `SignedLocnProof` protobuf to the user, which contains the vault key `vault_key`, user id `uid`, user nonce `unonce`, AP nonce `apnonce`, and sending time `time`
6. [Locn_proof] User sends the location proof to the verifier in order to verify the location claim
7. [m5/Token] Verifier sends a `SignedToken` protobuf to the user to validate/refuse the location proof, which contains the verifier nonce `vnonce` and location tag `locn_tag`

Based on the token, the user knows whether or not they successfully proved their location.

## Use

The user presses the button on the screen in order to begin the sequence of messages, as outlined above. After all communication finishes, the app will display whether or not the proof was successful.