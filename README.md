# simpleTeamWork

input:

RECEIVER:
    
    java Receiver receiver_port file.txt

SENDER:
    
    java Sender receiver_host_ip receiver_port file.txt MWS MSS timeout pdrop seed
    
output:


SENDER:

    snd 34.335 S 121 0 0
    rcv 34.4 SA 154 0 122
    snd 34.54 A 122 0 155
    snd 34.57 D 122 56 155
    drop 34.67 D 178 56 155
    rcv 36.56 A 155 0 178
    snd 134.67 D 178 56 155
    rcv 137.65 A 155 0 234
    snd 138.76 F 234 0 155
    rcv 140.23 FA 155 0 235
    snd 141.11 A 235 0 156

RECEIVER:

    same as SENDER




