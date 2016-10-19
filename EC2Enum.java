import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;



public class EC2Enum
{
	static
	{
		//disable apache's logging in amazon AWS SDK 
	      System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}
	

	public static void main(String[] args)
	{
				
		ClientConfiguration cc = new ClientConfiguration();
		//Possible to set a proxy to get out.
		//cc.setProxyHost("ipProxy");
		//cc.setProxyPort(3128);
		
		StringBuilder output = new StringBuilder();
		
		//get keys some way
		String accessKey = "AWS access key"
		String secretKey = "AWS secret key"
		String accountName = "Account name" //name associated to keys only used for output
		
		//basic check
		if (accessKey.length() != 0 && secretKey.length() != 0)
		{
			try
			{
				//looking in all AWS EC2 regions (us-east-1 us-west-2 us-west-1 eu-west-1 eu-central-1 ap-southeast-1 etc.)
				for(Regions regionRef : Regions.values())
				{
					BasicAWSCredentials creds = new BasicAWSCredentials(a.getAccessKey(), a.getSecretKey());
					try
					{
						//Building ec2 client for current region.
						AmazonEC2Client ec2cli = Region.getRegion(regionRef).createClient(AmazonEC2Client.class, new StaticCredentialsProvider(creds), cc);

						//ec2 instances are in sommething called "reservations"
						for (Reservation r : ec2cli.describeInstances().getReservations())
						{
							//looping for each instance
							for (Instance i : r.getInstances())
							{
								String ipStr = i.getPublicIpAddress();
								//check if instance is running and has a public ip.
								if (ipStr != null && !"null".equals(ipStr) && "running".equals(i.getState().getName()))
								{
									//append host to output, formatted for redis
									output.append("sadd AWS-");
									output.append(accountName.replace(' ', '_'));
									output.append('(');
									output.append(regionRef.getName().replace(' ', '_'));
									output.append(") ");
									output.append(ipStr);
									output.append('\n');
								}
							}
						}
					}
					//catch "unauthorized" on some regions (us-gov and north korea?)
					catch (AmazonServiceException e)
					{
						continue;
					} 
				}
			}
			catch (AmazonServiceException e)
			{
				//append error to output, commented in redis
				output.append("#ERROR ");
				output.append(a.getAccountName());
				output.append(' ');
				output.append(e.getErrorMessage());
				output.append('\n');
			}
		}
		
		System.out.print(output.toString().trim());	
	}
}