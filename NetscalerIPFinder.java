import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import com.citrix.netscaler.nitro.exception.nitro_exception;
import com.citrix.netscaler.nitro.resource.config.basic.server;
import com.citrix.netscaler.nitro.resource.config.basic.server_servicegroup_binding;
import com.citrix.netscaler.nitro.resource.config.basic.servicegroup;
import com.citrix.netscaler.nitro.resource.config.basic.servicegroup_servicegroupmember_binding;
import com.citrix.netscaler.nitro.resource.config.cs.csaction;
import com.citrix.netscaler.nitro.resource.config.cs.cspolicy;
import com.citrix.netscaler.nitro.resource.config.cs.cspolicy_csvserver_binding;
import com.citrix.netscaler.nitro.resource.config.cs.csvserver;
import com.citrix.netscaler.nitro.resource.config.cs.csvserver_cspolicy_binding;
import com.citrix.netscaler.nitro.resource.config.cs.csvserver_responderpolicy_binding;
import com.citrix.netscaler.nitro.resource.config.lb.lbvserver;
import com.citrix.netscaler.nitro.resource.config.lb.lbvserver_servicegroup_binding;
import com.citrix.netscaler.nitro.resource.config.responder.responderpolicy;
import com.citrix.netscaler.nitro.service.nitro_service;

public class NetscalerIPFinder
{
	private static final String IPV4_REGEX = "\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z";
	
	private static final String subNet172_16_31 = "172\\.(1[6-9]|2[0-9]|3[0-1])\\..*";
	
	public static final String[] netscalers = {"list","of","your","netscaler","hostname or IP"};
	
	public static String searchIp;
	
	private static String url;
	private static String userName;
	private static String password;
	
	public static void main(String[] args) throws Exception
	{
		if(args.length != 3 && args.length != 2)
		{
			System.out.println("Usage: java -jar netscalerIpFinder.jar publicIP [domain|url] userName");
			System.out.println("\tFind where is internally redirected traffic on that IP and domain|url");
			System.out.println("-OR-");
			System.out.println("Usage: java -jar netscalerIpFinder.jar publicIP userName");
			System.out.println("\tFind all internal server that could be reached with that IP");
			System.out.println("-OR-");
			System.out.println("Usage: java -jar netscalerIpFinder.jar privateIP userName");
			System.out.println("\tFind public url|domain redirecting to that internal IP");
			
			return;
		}
		nitro_service nService = null;
		
		searchIp = args[0];
		
		if(searchIp == null || searchIp.length() < 7 || !searchIp.matches(IPV4_REGEX))
		{
			System.out.println("IPv4 address expected for IP param.");
			return;
		}
		
		if(args.length == 2)
		{
			userName = args[1];
		}
		else if(args.length == 3)
		{
			url = args[1];
			userName = args[2];
		}
		

		System.out.println("Enter password for user "+userName+": ");
		password = new String(System.console().readPassword());
		
		if(args.length == 2 && (searchIp.startsWith("10.") || searchIp.matches(subNet172_16_31) ))
		{
			searchFromPrivate();			
			return;
		}
			
		ArrayList<csvserver> csvserverMatchingIp = null;
		String currentEndpoint = "";
		csvserver_cspolicy_binding policyBindings [] = null;
		csvserver currentCSV = null;
		
		
		boolean found = false;
		for (String endpoint : netscalers)
		{
			currentEndpoint = endpoint;
			System.out.println("\n\n"+endpoint+": \n");
			try
			{
				nService = new nitro_service(endpoint, "https");
				nService.set_certvalidation(false);
				nService.set_hostnameverification(false);
				try
				{
					nService.login(userName, password);
				}
				catch (nitro_exception e)
				{
					e.printStackTrace();
					System.exit(1);
				}
				csvserverMatchingIp = new ArrayList<csvserver>();
				for (csvserver csv : csvserver.get(nService))
				{
					if (searchIp.equals(csv.get_ipv46()))
					{
						csvserverMatchingIp.add(csv);
					}
				}
				if (!csvserverMatchingIp.isEmpty())
				{
					for (csvserver csv : csvserverMatchingIp)
					{
						currentCSV = csv;
						policyBindings = csvserver_cspolicy_binding.get(nService, csv.get_name());
						csvserver_responderpolicy_binding[] crb = csvserver_responderpolicy_binding.get(nService, csv.get_name());
						if(policyBindings != null)
						{
							for (csvserver_cspolicy_binding ccb : policyBindings)
							{
								cspolicy policy = cspolicy.get(nService, ccb.get_policyname());
								if (policy != null)
								{
									String rule = policy.get_rule();
									if (args.length == 2 || (rule != null && rule.contains(url.trim().toLowerCase())))
									{
										lbvserver lbServer = lbvserver.get(nService, csaction.get(nService, policy.get_action()).get_targetlbvserver());
										System.out.println("CS VServer: " + csv.get_name());
										displayResponderPolicies(nService, crb);
										System.out.println("\tCS Policy: " + ccb.get_policyname());
										System.out.println("\t\tCS Policy Rule: " + rule);
										System.out.println("\t\t\tCS Action: " + policy.get_action());
										found = fromLBVServer(nService, lbServer);
									}
								}
							} 
						}
						else
						{
							String defaultLBVSname = csv.get_lbvserver();
							if(defaultLBVSname != null && !defaultLBVSname.equals(""))
							{
								lbvserver lbServer = lbvserver.get(nService, defaultLBVSname);
								if(lbServer != null)
								{
									System.out.println("CS VServer: " + csv.get_name());
									displayResponderPolicies(nService, crb);
									System.out.println("\tUses default LB VServer binding.");
									found = fromLBVServer(nService, lbServer);
								}
							}
						}
					}
				}
				else
				{
					ArrayList<lbvserver> lbvserverMatchingIp = new ArrayList<lbvserver>();
					for (lbvserver lbv : lbvserver.get(nService))
					{
						if (searchIp.equals(lbv.get_ipv46()))
						{
							lbvserverMatchingIp.add(lbv);
						}
					}

					found = fromLBVServers(nService, lbvserverMatchingIp);
				} 
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.err.println("Error dump:");
				System.err.println("\tEndpoint: "+currentEndpoint);
				System.err.println("\tMatched CS VServers:");
				if(csvserverMatchingIp != null)
				{
					for (csvserver csv : csvserverMatchingIp)
					{
						System.err.println("\t\t" + csv.get_name());
					} 
				}
				else
				{
					System.err.println("\t\tnone");
				}
				System.err.println("\n\tCurrent CS VServer: "+(currentCSV == null? "None (null)" : currentCSV.get_name())); //ternary...
				if(currentCSV != null)
				{
					System.err.println("\tPolicies of current CS VServer: ");
					if(policyBindings != null)
					{
						for (csvserver_cspolicy_binding ccb : policyBindings)
						{
							System.err.println("\t\t"+ccb.get_policyname());
						}
					}
					else
					{
						System.err.println("\t\tnone");
					}
				}
				
			}
			
			if(nService != null)
			{
				nService.logout();
			}
			
			if(found)
			{
				break;
			}
		}		
	}
	
	private static void displayResponderPolicies(nitro_service nService, csvserver_responderpolicy_binding[] crbs) throws Exception
	{
		if(crbs != null && crbs.length > 0)
		{
			System.out.println("Responder polic"+(crbs.length > 1 ? "ies" : "y")+":"); //ternary again, I hate myself
			for(csvserver_responderpolicy_binding crb : crbs)
			{
				responderpolicy responderPolicy = responderpolicy.get(nService, crb.get_policyname());
				if(responderPolicy != null)
				{
					System.out.println("\t"+crb.get_policyname()+":\n\t\t"+responderPolicy.get_rule()+"\n\t\t-> "+responderPolicy.get_action());
				}
			}
		}
	}

	private static boolean fromLBVServers(nitro_service nService, ArrayList<lbvserver> lbs) throws Exception
	{
		boolean found = false;
		for(lbvserver lbv : lbs)
		{
			found |= fromLBVServer(nService, lbv);
		}
		return found;
	}
	
	private static boolean fromLBVServer(nitro_service nService, lbvserver lbs) throws Exception
	{
		
		boolean found = false;
		lbvserver_servicegroup_binding[] lsb = lbvserver_servicegroup_binding.get(nService, lbs.get_name());
		
		for(lbvserver_servicegroup_binding sg : lsb)
		{
			System.out.println("\t\t\t\tLB VServer: "+lbs.get_name());
			System.out.println("\t\t\t\t\t LB Service Group: "+sg.get_servicegroupname());
			int i = 0;
			for(servicegroup_servicegroupmember_binding ssb : servicegroup_servicegroupmember_binding.get(nService, sg.get_servicegroupname()))
			{
				
				String serverInfo = ssb.get_ip();
				
				try//checking if hostname in netscaler has another name
				{
					InetAddress ia = InetAddress.getByName(ssb.get_servername()+".your.org.suffix");//generally the suffix is not in netscaler
					InetAddress ia2 = InetAddress.getByName(ssb.get_ip());
					
					if(ia != null && !ia.equals(ia2))
					{
						serverInfo += " -> "+ia.getHostAddress();
					}
					
				}
				catch (UnknownHostException e){/*OSEF*/}				
				
				
				System.out.println("\t\t\t\t\t\tServer "+i++);
				System.out.println("\t\t\t\t\t\t\tState: "+ssb.get_svrstate());
				System.out.println("\t\t\t\t\t\t\tServer Name: "+ssb.get_servername());
				System.out.println("\t\t\t\t\t\t\tInternal IP: "+serverInfo);
				System.out.println("\t\t\t\t\t\t\tPort Redirection: "+ssb.get_port());
				found = true;
			}
		}
		
		return found;
	}

	private static void searchFromPrivate()
	{
		String currentEndpoint = "";
		nitro_service nService = null;

		System.out.println("Magic is happening, could take some time...");
		
		for (String endpoint : netscalers)
		{
			currentEndpoint = endpoint;
			System.out.print("Looking at "+endpoint+": ");
			//System.out.println("\n\n"+endpoint+": \n");
			try
			{
				nService = new nitro_service(endpoint, "HTTPS");
				nService.set_certvalidation(false);
				nService.set_hostnameverification(false);
				try
				{
					nService.login(userName, password);
				}
				catch (nitro_exception e)
				{
					e.printStackTrace();
					System.exit(1);
				}
				
				HashMap<servicegroup, server_servicegroup_binding> matchSGs = new HashMap<servicegroup, server_servicegroup_binding>();
				
				for(server s : server.get(nService))
				{
					if(searchIp.equals(s.get_ipaddress()))
					{
						server_servicegroup_binding[] ssbs = server_servicegroup_binding.get(nService, s.get_name());
						if(ssbs != null)
						{
							for (server_servicegroup_binding ssb : ssbs)
							{
								matchSGs.put(servicegroup.get(nService, ssb.get_servicegroupname()), ssb);
							} 
						}
					}
				}
				
				if(matchSGs.isEmpty())
				{
					System.out.println("nothin'!");
					continue;
				}
				
				HashMap<lbvserver, servicegroup> matchLBVSs = new HashMap<lbvserver, servicegroup>();
				
				for(lbvserver lbv : lbvserver.get(nService))
				{
					lbvserver_servicegroup_binding[] lsbs = lbvserver_servicegroup_binding.get(nService, lbv.get_name());
					
					if(lsbs == null)
					{
						continue;
					}
					
					for(lbvserver_servicegroup_binding lsb : lsbs)
					{
						for(servicegroup sg : matchSGs.keySet())
						{
							if(sg.get_servicegroupname().equals(lsb.get_servicegroupname()))
							{
								matchLBVSs.put(lbv, sg);
							}
						}
					}
				}
				
				if(matchLBVSs.isEmpty())
				{
					System.out.println("nothin'!");
					continue;
				}
				
				HashMap<csaction, lbvserver> matchCSAs = new HashMap<csaction, lbvserver>();
				
				for(csaction csa : csaction.get(nService))
				{
					for(lbvserver lbv : matchLBVSs.keySet())
					{
						if(lbv.get_name().equals(csa.get_targetlbvserver()))
						{
							matchCSAs.put(csa, lbv);
						}
					}
				}
				
				if(matchCSAs.isEmpty())
				{
					System.out.println("nothin'!");
					continue;
				}
				
				HashMap<cspolicy, csaction> matchCSPs = new HashMap<cspolicy, csaction>();
				
				for(cspolicy csp : cspolicy.get(nService))
				{
					for(csaction csa : matchCSAs.keySet())
					{
						if(csa.get_name().equals(csp.get_action()))
						{
							matchCSPs.put(csp, csa);
						}
					}
				}
				
				if(matchCSPs.isEmpty())
				{
					System.out.println("nothin'!");
					continue;
				}
				
				for(cspolicy csp : matchCSPs.keySet())
				{
					for(cspolicy_csvserver_binding ccb : cspolicy_csvserver_binding.get(nService, csp.get_policyname()))
					{
						csvserver csvs = csvserver.get(nService, ccb.get_domain());
						csaction csa = matchCSPs.get(csp);
						lbvserver lbvs = matchCSAs.get(csa);
						servicegroup sg = matchLBVSs.get(lbvs);
						server_servicegroup_binding ssb = matchSGs.get(sg);
						
						System.out.println("\n"+csvs.get_name());
						System.out.println("\t"+csp.get_rule()+" "+csvs.get_ipv46()+":"+csvs.get_port()+" "+csvs.get_servicetype());
						
						System.out.println("\t\tState: "+ssb.get_svrstate());
						System.out.println("\t\tServer Name: "+ssb.get_name());
						System.out.println("\t\tInternal IP: "+ssb.get_serviceipaddress());
						System.out.println("\t\tPort Redirection: "+ssb.get_port());
					}
				}
				
				if(nService != null)
				{
					nService.logout();
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.err.println("Current NS: "+currentEndpoint);
			}
		}
				
		
	}
	
}
