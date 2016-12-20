# CRAPZ

- ipChecker.sh : Checks your public IP and sends an email if changed. Dependance: curl, sendemail, libio-socket-ssl-perl, libnet-ssleay-perl (for sendemail TLS). Replace with your values and copy file in /etc/cron.hourly or somethin' like that.

- EC2Enum.java : Enum running EC2 instance with public ip. Requires AWS Java SDK.

- NetscalerIPFinder.java : Cause Netscaler HTTP interface sucks. Try to link (domain name|public IP) to (internal name|private IP) through CSVServer, CSPolicy, LBVserver and Service Group. Also displays responder policies if available. See usage. Requires Citrix Netscaler "nitro" SDK.
