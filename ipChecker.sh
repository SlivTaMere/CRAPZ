#!/bin/bash

ipFile="file/to/store/ip"

ip=$(curl -s -k https://api.ipify.org)
if [ $(cat $ipFile) != $ip ]
then
	echo -n "$ip" > $ipFile
	sendemail -q -f FROMemail -t TOemail -m "new IP: $ip" -s smtp.gmail.com:587 -xu gmailUserName -xp gmailPassword -o tls=yes -o
fi
