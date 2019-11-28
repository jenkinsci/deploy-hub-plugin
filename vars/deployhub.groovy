// vars/deployhub.groovy
class deployhub
{
  private String body = "";
  private String message = "";
  private String cookie = "";
  private String url = "";
  private String userid = "";
  private String pw = "";
  private Integer statusCode;
  private boolean failure = false;


  @NonCPS
  def private _getURL(text)
  {
    def matcher = text = ~'<serverURL>(.+)</serverURL>'
    matcher ? matcher[0][1] : null
  }

  def private getURL(env)
  {
    def config = "${env.JENKINS_HOME}/org.jenkinsci.plugins.deployhub.DeployHub.xml";
    return _getURL(new File(config).text);
  }

  def private String msg()
  {
    return "Loading dhactions";
  }

  def private parseResponse(HttpURLConnection connection)
  {
    this.statusCode = connection.responseCode;
    this.message = connection.responseMessage;
    this.failure = false;

    if (statusCode == 200 || statusCode == 201)
    {
      this.body = connection.content.text; //this would fail the pipeline if there was a 400    
    }
    else
    {
      this.failure = true;
      this.body = connection.getErrorStream().text;
    }

    /*        Map<String, List<String>> map = connection.getHeaderFields();
            
            if (cookie.length() == 0)
            { 
             for (Map.Entry<String, List<String>> entry : map.entrySet()) 
             {
              if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) 
              {                  
                String c = entry.getValue();
                if  (c.contains("p1=") || c.contains("p2="))
                {
                  cookie = c;
                }  
              }
             }     
            } */
  }

  def private doGetHttpRequest(String requestUrl)
  {
    URL url = new URL(requestUrl);
    HttpURLConnection connection = url.openConnection();

    connection.setRequestMethod("GET");
    connection.setRequestProperty("Cookie", cookie);
    connection.doOutput = true;

    //get the request    
    connection.connect();

    //parse the response    
    parseResponse(connection);

    if (failure)
    {
      error("\nGET from URL: $requestUrl\n  HTTP Status: $resp.statusCode\n  Message: $resp.message\n  Response Body: $resp.body");
    }

    this.printDebug("Request (GET):\n  URL: $requestUrl");
    this.printDebug("Response:\n  HTTP Status: $resp.statusCode\n  Message: $resp.message\n  Response Body: $resp.body");
  }

  /**    
   * Gets the json content to the given url and ensures a 200 or 201 status on the response.    
   * If a negative status is returned, an error will be raised and the pipeline will fail.    
   */
  def private Object doGetHttpRequestWithJson(String userid, String pw, String requestUrl)
  {
    return doHttpRequestWithJson(userid, pw, "", requestUrl, "GET");
  }

  /**    
   * Posts the json content to the given url and ensures a 200 or 201 status on the response.    
   * If a negative status is returned, an error will be raised and the pipeline will fail.    
   */
  def private Object doPostHttpRequestWithJson(String userid, String pw, String json, String requestUrl)
  {
    return doHttpRequestWithJson(userid, pw, json, requestUrl, "POST");
  }

  /**    
   * Posts the json content to the given url and ensures a 200 or 201 status on the response.    
   * If a negative status is returned, an error will be raised and the pipeline will fail.    
   */
  def private Object doPutHttpRequestWithJson(String userid, String pw, String json, String requestUrl)
  {
    return doHttpRequestWithJson(userid, pw, json, requestUrl, "PUT");
  }

  def private String cleanName(String name)
  {
   if (name == null)
     return name;

   name = name.replaceAll("\\.","_"); 
   name = name.replaceAll("-","_"); 
   return name;
  }
  /**    
   * Post/Put the json content to the given url and ensures a 200 or 201 status on the response.    
   * If a negative status is returned, an error will be raised and the pipeline will fail.    
   * verb - PUT or POST    
   */
  def private String enc(String p)
  {
    return java.net.URLEncoder.encode(p, "UTF-8");
  }

  def private Object doHttpRequestWithJson(String userid, String pw, String json, String requestUrl, String verb)
  {
   if (userid.length() == 0)
    userid = "@deployhub-creds";

   if (userid.indexOf('@') >= 0)
   {
    def cred = userid.substring(1);
    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class, Jenkins.instance, null, null ).find{it.id == cred};
    def username = creds.username;
    def password = creds.password;

     URL url = new URL(requestUrl);
     HttpURLConnection connection = url.openConnection();

     connection.setRequestMethod(verb);
     connection.setRequestProperty("Content-Type", "application/json");
     connection.setRequestProperty("Cookie", "p1=$username; p2=$password");
     connection.doOutput = true;

     if (json.length() > 0)
     {
       //write the payload to the body of the request    
       def writer = new OutputStreamWriter(connection.outputStream);
       writer.write(json);
       writer.flush();
       writer.close();
     }
  
     //post the request    
     connection.connect();

     //parse the response    
     parseResponse(connection);

     if (failure)
     {
      error("\n$verb to URL: $requestUrl\n    JSON: $json\n    HTTP Status: $statusCode\n    Message: $message\n    Response Body: $body");
      return null;
     }

     return jsonParse(body);
   }
   else
   {
     URL url = new URL(requestUrl);
     HttpURLConnection connection = url.openConnection();

     connection.setRequestMethod(verb);
     connection.setRequestProperty("Content-Type", "application/json");
     connection.setRequestProperty("Cookie", "p1=$userid; p2=$pw");
     connection.doOutput = true;

     if (json.length() > 0)
     {
       //write the payload to the body of the request    
       def writer = new OutputStreamWriter(connection.outputStream);
       writer.write(json);
       writer.flush();
       writer.close();
     }
  
     //post the request    
     connection.connect();

     //parse the response    
     parseResponse(connection);

     if (failure)
     {
      error("\n$verb to URL: $requestUrl\n    JSON: $json\n    HTTP Status: $statusCode\n    Message: $message\n    Response Body: $body");
      return null;
     }

     return jsonParse(body);
    }   
  }

  @NonCPS
  def private jsonParse(def json)
  {
    new groovy.json.JsonSlurperClassic().parseText(json)
  }

  /**
   * Move an application version to another stage of the pipeline
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param Application the application to move
   * @param FromDomain the domain to move from
   * @param Task the move task
   * @return Array with first element being the return code, second the msg
   **/

  def moveApplication(String url, String userid, String pw, String Application, String FromDomain, String Task)
  {
    // Get appid
    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/application/" + enc(Application));
    def appid = data.result.id;

    // Get from domainid
    data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/domain/" + enc(FromDomain));
    def fromid = data.result.id;

    // Get from Tasks
    data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/GetTasks?domainid=" + fromid);
    if (data.size() == 0)
      return [false, "Could not move the Application '" + Application + "' from '" + FromDomain + "' using the '" + Task + "' Task"];

    def i = 0;
    def taskid = 0;
    for (i = 0; i < data.size(); i++)
    {
      if (data[i].name.equalsIgnoreCase(Task))
      {
        taskid = data[i].id;
        break;
      }
    }

    // Move App Version
    data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/RunTask?f=run&tid=" + taskid + "&notes=&id=" + appid + "&pid=" + fromid);
    if (data.size() == 0)
      return [false, "Could not move the Application '" + Application + "' from '" + FromDomain + "' using the '" + Task + "' Task"];
    else
    {
      if (data.result)
        return [true, "Moved Application '" + Application + "' from '" + FromDomain + "'"];
      else
        return [false, "Could not move the Application '" + Application + "' from '" + FromDomain + "' using the '" + Task + "' Task"];
    }
  }

  /**
   * Force a deployment to an environment
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param Environment Text the environment to reset the deployment
   * @param checkRunning boolean if true then only reset non-running server.  if false ignore running state and reset
   **/

  def forceDeployIfNeeded(String url, String userid, String pw, String Environment, boolean checkRunning)
  {
    def data = getServersInEnvironment(url, userid, pw, Environment);

    def servers = data[1]['result']['servers'];

    def i = 0;
    for (i = 0; i < servers.size(); i++)
    {
      def id = servers[i]['id'];

      if (checkRunning)
      {
       data = ServerRunning(url, userid, pw, "$id");

       def running = data[1]['result']['data'][0][4];

       if (running.equalsIgnoreCase("false"))
         doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/mod/server/$id/?force=y");
      }
      else
      {
       doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/mod/server/$id/?force=y"); 
      }   
    }
  }

  /**
   * List servers in an environment
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param Environment the application to move
   * @return Array with first element being the return code, second Array of servers
   **/

  def getServersInEnvironment(String url, String userid, String pw, String Environment)
  {
    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/environment/" + enc(Environment));
    if (data.size() == 0)
      return [false, "Could not get environment: " + Environment];
    else
      return [true, data];
  }

  /**
   * Ping the server to see if its running
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param server the server to test
   * @return Array with first element being the return code, second details about the server
   **/

  def isServerRunning(String url, String userid, String pw, String server)
  {
    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/testserver/" + enc(server));
    if (data.size() == 0)
      return [false, "Could not test server '" + server];
    else
      return [true, data];
  }

  /**
   * Deploy an application to an environment
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param Application Text the Application to deploy
   * @param Environment Text the target Environment
   * @return Array with first element being the return code, second the deployment id
   **/

  def deployApplication(String url, String userid, String pw, String Application, String Environment)
  {
    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/deploy/" + enc(Application) + "/" + enc(Environment) + "?wait=N");
    if (data.size() == 0)
      return [false, "Could not Deploy Application '" + Application + "' to Environment '" + Environment + "'"];
    else
      return [true, data];
  }

  /**
   * Get the deployment logs
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param deployid Text the deployment id to check
   * @return Array with first element being the return code, second the log data
   **/

  def getLogs(String url, String userid, String pw, String deployid)
  {
    def done = 0;

    while (done == 0)
    {
      def res = this.isDeploymentDone(url, userid, pw, "$deployid");

      if (res != null)
      {
        if (res[0])
        {
          def s = res[1];

          if (res[1]['success'] && res[1]['iscomplete'])
            done = 1;
        }
        else
          done = 1;
      }

      sleep(10000); // 10 seconds
    }

    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/log/" + deployid);

    if (data == null || data.size() == 0)
      return [false, "Could not get log #" + deployid];

    def lines = data['logoutput'];
    def output = "";

    def i = 0;
    for (i = 0; i < lines.size(); i++)
    {
      output += lines[i] + "\n";
    }

    return [true, output];
  }

  /**
   * Check to see if a deployment is done
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param deployid Text the deployment id to check
   * @return Array with first element being the return code, second true/false
   **/

  def isDeploymentDone(String url, String userid, String pw, String deployid)
  {
    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/log/" + deployid + "?checkcomplete=Y");

    if (data == null)
      return [false, "Could not get log #" + deployid];

    if (data != null && data.size() == 0)
      return [false, "Could not get log #" + deployid];

    return [true, data];
  }

  /**
   * Approve the application version for its current pipeline stage
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param Application Text the Application to approve
   * @return Array with first element being the return code, second approval results
   **/

  def approveApplication(String url, String userid, String pw, String Application)
  {
    // Get appid
    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/application/" + enc(Application));

    def appid = data.result.id;

    // Approve appid
    data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/approve/" + appid);
    if (data.size() == 0)
      return [false, "Could not Approve Application '" + Application + "'"];
    else
      return [true, data];
  }

  /**
   * Update the name of the component
   * @param url Text the url to the DeployHub server
   * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
   * @param pw Text the DeployHub password
   * @param compname Text the new name of the component
   * @param compvariant Text the new variant of the component    
   * @param compversion Text the new version of the component 
   * @param compid Integer the id of the component to update  
   * @return Array with first element being the return code, second approval results
   **/
  def updateName(String url, String userid, String pw, String compname, String compvariant, String compversion, Integer compid)
    {
      def data;

      compvariant = cleanName(compvariant);
      compversion = cleanName(compversion);

      if (compvariant == "" && compversion != null && compversion != "")
      {
       compvariant = compversion;
       compversion = null;  
      }

      if (compname.indexOf('.') >= 0)
      {
       compname = compname.tokenize('.').last();
      }

      if (compvariant != null && compvariant != "" && compversion != null && compversion != "")
        data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateSummaryData?objtype=23&id=" + compid + "&change_1=" + enc(compname + ";" + compvariant + ";" + compversion));
      else if (compvariant != null && compvariant != "")
        data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateSummaryData?objtype=23&id=" + compid + "&change_1=" + enc(compname + ";" + compvariant));
      else
        data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateSummaryData?objtype=23&id=" + compid + "&change_1=" + enc(compname));

      return data;
    }

  /**
    * New Component item for the passed Component 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compid Integer the component that the item belongs to
    * @param kind Text the kind of the item (docker, database, file)   
    * @return Boolean success/failure
    **/

  def newComponentItem(String url, String userid, String pw, Integer compid, String kind, List component_items)
  {
    def data;
    // Get compId   
    if (kind.equalsIgnoreCase("docker") || component_items == null) 
       data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateAttrs?f=inv&c=" + compid + "&xpos=100&ypos=100&kind=" + kind + "&removeall=Y");
    else
    {
     def ypos = 100;
     
     def i = 0;
     def parent_item = -1;

     for (Map item : component_items)
     {
      def str = "";
      def ciname = "";
      for (entry in item) 
      {
        if (entry.key.equalsIgnoreCase("name"))
         ciname = entry.value;
        else
         str += "&" +enc(entry.key) + "=" + enc(entry.value);
      }

      if (i == 0)
       str += "&removeall=Y";

      data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/new/compitem/" + enc(ciname) + "?component=" + compid + "&xpos=100&ypos=" + ypos + "&kind=" + kind + str);
      
      if (parent_item > 0)
        doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateAttrs?f=iad&c=" + compid + "&fn=" + parent_item + "&tn=" + data.result.id);
      parent_item = data.result.id;
      ypos += 100;
      i++;
     }
    }   
    return data;
  }

  /**
    * New Docker Component  
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @param kind Text use "docker" for a container type component
    * @param parent_compid Integer the parent to derive the new version from.  Use -1 for base version
    * @return Boolean success/failure
    **/

  def newComponent(String url, String userid, String pw, String compname, String compvariant, String compversion, String kind, Integer parent_compid)
  {
   if (kind.equalsIgnoreCase("docker"))
    return newDockerComponent(url, userid, pw, compname, compvariant, compversion, parent_compid);
   else
    return newFileComponent(url, userid, pw, compname, compvariant, compversion, parent_compid, null);
  }

  /**
    * New Docker Component  
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @param parent_compid Integer the parent to derive the new version from.  Use -1 for base version
    * @return Boolean success/failure
    **/

  def newDockerComponent(String url, String userid, String pw, String compname, String compvariant, String compversion, Integer parent_compid)
  {
    compvariant = cleanName(compvariant);
    compversion = cleanName(compversion);

    if (compvariant == "" && compversion != null && compversion != "")
    {
      compvariant = compversion;
      compversion = null;  
    }

    def compid = 0;
    def data;
    // Create base version
    if (parent_compid < 0)
    {
      data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/new/compver/" + enc(compname + ";" + compvariant));
      compid = data.result.id;
    }
    else
    {
      data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/new/compver/" + parent_compid);
      compid = data.result.id;
    }

    updateName(url, userid, pw, compname, compvariant, compversion, compid);

    newComponentItem(url, userid, pw, compid, "docker", null);

    return compid;
  }

  /**
    * New Component  
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @param kind Text use "docker" for a container type component
    * @param parent_compid Integer the parent to derive the new version from.  Use -1 for base version
    * @param component_items List an array of Maps for the component item properties  
    * @return Boolean success/failure
    **/

  def newFileComponent(String url, String userid, String pw, String compname, String compvariant, String compversion, Integer parent_compid, List component_items)
  {
    compvariant = cleanName(compvariant);
    compversion = cleanName(compversion);

    if (compvariant == "" && compversion != null && compversion != "")
    {
      compvariant = compversion;
      compversion = null;  
    }

    def compid = 0;
    def data;
    // Create base version
    if (parent_compid < 0)
    {
      data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/new/compver/" + enc(compname + ";" + compvariant));
      compid = data.result.id;
    }
    else
    {
      data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/new/compver/" + parent_compid);
      compid = data.result.id;
    }

    updateName(url, userid, pw, compname, compvariant, compversion, compid);
    
    newComponentItem(url, userid, pw, compid, "file", component_items);
  
    return compid;
  }

  /**
    * Get the Component Id 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @return component id, -1 for not found
    **/

  def newComponentVersion(String url, String userid, String pw, String compname, String compvariant, String compversion)
  {
   return newComponentVersion(url, userid, pw, compname, compvariant, compversion, "docker", null);
  }

  /**
    * Get the Component Id 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @return component id, -1 for not found
    **/

  def newComponentVersion(String url, String userid, String pw, String compname, String compvariant, String compversion, String kind, List component_items)
  {
    compvariant = cleanName(compvariant);
    compversion = cleanName(compversion);

    if (compvariant == "" && compversion != null && compversion != "")
    {
      compvariant = compversion;
      compversion = null;  
    }

    // Get latest version of compnent variant
    def data = getComponent(url, userid, pw, compname, compvariant, compversion);
    if (data[0] == -1)
    {
     data = getComponent(url, userid, pw, compname, compvariant, null);
     if (data[0] == -1)
       data = getComponent(url, userid, pw, compname, "", null);
    }

    def compid = data[0];
    def found_compname = data[1];
    def check_compname = "";

    def short_compname = "";

    if (compname.indexOf('.') >= 0)
    {
     short_compname = compname.tokenize('.').last();
    }

    if (compvariant != null && compvariant != "" && compversion != null && compversion != "")
        check_compname = short_compname + ";" + compvariant + ";" + compversion;
    else if (compvariant != null && compvariant != "")
        check_compname = short_compname + ";" + compvariant;
      else
        check_compname = short_compname;

    // Create base component variant
    // if one is not found
    // Get the new compid of the new component variant
    if (compid < 0)
    {
      if (compversion == null || compversion == "") 
        compid = newComponent(url, userid, pw, compname, "", "", "", -1);
      else
        compid = newComponent(url, userid, pw, compname, compvariant, "", "", -1);
    }

    // Create component items for the component 
    if (found_compname == "" || found_compname != check_compname)
    {
     if (kind.equalsIgnoreCase("docker"))
       compid = newDockerComponent(url, userid, pw, compname, compvariant, compversion, compid);
     else
       compid = newFileComponent(url, userid, pw, compname, compvariant, compversion, compid, component_items);     
    }
    else if (compid > 0)
    {
     if (kind.equalsIgnoreCase("docker"))
       newComponentItem(url, userid, pw, compid, "docker", null);
     else
       newComponentItem(url, userid, pw, compid, "file", component_items); 
    }
      
    return compid;
  }

  /**
    * Get the Component Id 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @return component id, "" for not found
    **/

  def getComponent(String url, String userid, String pw, String compname, String compvariant, String compversion)
  {
    compvariant = cleanName(compvariant);
    compversion = cleanName(compversion);

    if (compvariant == "" && compversion != null && compversion != "")
    {
      compvariant = compversion;
      compversion = null;  
    }

    def Component = "";

    if (compvariant != null && compvariant != "" && compversion != null && compversion != "")
        Component = compname + ";" + compvariant + ";" + compversion;
    else if (compvariant != null && compvariant != "")
        Component = compname + ";" + compvariant;
      else
        Component = compname;

    def check_compname = "";
    def short_compname = "";

    if (compname.indexOf('.') >= 0)
    {
     short_compname = compname.tokenize('.').last();
    }

    if (compvariant != null && compvariant != "" && compversion != null && compversion != "")
        check_compname = short_compname + ";" + compvariant + ";" + compversion;
    else if (compvariant != null && compvariant != "")
        check_compname = short_compname + ";" + compvariant;
      else
        check_compname = short_compname;

    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/component/" + enc(Component));

    if (data == null)
      return [-1, ""];

    if (data.success)
    {
      def compid = data.result.id;
      def name = data.result.name;

      if (name != check_compname)
      {
        def vers = data.result.versions;
        for (v in vers)
        {
          if (v.name == check_compname)
          {
            compid = v.id;
            name = v.name;
            break;
          }
        }
      }
      return [compid, name];
    }
    else
    {
      return [-1, ""];
    }
  }

  /**
    * Get the Application Id 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @return component id, "" for not found
    **/

  def getApplication(String url, String userid, String pw, String appname, String appversion)
  {
    appversion = cleanName(appversion);

    def Application = "";

    if (appversion != null && appversion != "")
      Application = appname + ";" + appversion;
    else
      Application = appname;

    def check_appname = "";
    def short_appname = "";

    if (appname.indexOf('.') >= 0)
    {
     short_appname = appname.tokenize('.').last();
    }

    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/application/" + enc(Application));

    if (data == null)
      return [-1, "", -1];

    if (data.success)
    {
      def appid = data.result.id;
      def name  = data.result.name;
      def vlist = data.result.versions;
      def latest = -1;

      if (vlist != null && vlist.size() > 0 && vlist.last() != null)
      {
        latest = vlist.last().id;
        return [appid, name, latest];
      }
      else
        return [appid, name, appid];
    }
    else
    {
      return [-1, "", -1];
    }
  }

  /**
    * New Application Version  
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param appname Text the application name
    * @param appversion Text the version of the application
    * @param envs String Array the environments the base version should be assigned to
    * @return Boolean success/failure
    **/

  def newApplication(String url, String userid, String pw, String appname, String appversion, String[] envs)
  {
    appversion = cleanName(appversion);

    def appid = 0;
    def data;
    def parent_appid = -1;

    def domain = ""
    if (appname.indexOf('.') >= 0)
    {
     def parts = appname.tokenize('.');
     if (parts.size() > 0)
        parts.remove( parts.size() - 1 );
     domain = parts.join('.');
     domain="domain=" + enc(domain);
     appname = appname.tokenize('.').last();
    }

    // Get Base Version
    data = getApplication(url,userid,pw,appname,"");
    parent_appid = data[0];

    // Create base version
    if (parent_appid < 0)
    {
      data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/new/application/" + enc(appname) + "?" + domain);
      if (data.success)
      {
       data = getApplication(url,userid,pw,appname,"");
       parent_appid = data[0];
      } 
      
      if (envs != null)
      {
        for (def i=0;i<envs.size();i++)
        {
         data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/assign/application/" + enc(appname) + "/" + enc(envs[i])); 
        }
      }
    }
    
    // Refetch parent to get version list
    data = getApplication(url,userid,pw,appname,"");
    def latest_appid = data[2];

    // Refetch the current app version to see if we need to create it or not
    data = getApplication(url,userid,pw,appname, appversion);
    appid = data[0];

    if (appid < 0)
    {  
     data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/newappver/" + latest_appid + "/?name=" + enc(appname + ";" + appversion) + "&" + domain);

     if (!data.success)
      return [-1,data.error]; 

     appid = data.result.id;
    } 
    
    return [appid,""];
  }

   /**
    * Get a Component version's base component id 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compid Integer the component version id
    * @return Integer the base component id 
    **/

  def getBaseComponent(String url, String userid, String pw, Integer compid)
  {
    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/component/" + compid);
    
    if (data == null)
      return [-1, "", -1];

    def basecompid = -1;
    while (data.result.predecessor != null)
    {
     data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/component/" + data.result.predecessor.id);
    
     if (data == null)
       break;
    }
    return data.result.id; 
  }  

 /**
    * Adds a Component version to Application Version, replacing existing component versions derived from the same base  
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param appid Integer the application version id
    * @param compid Integer the component version id
    * @return Boolean success/failure
    **/

  def addCompVer2AppVer(String url, String userid, String pw, Integer appid, Integer compid)
  {
    def replaceCompId = -1;
    def basecompid = getBaseComponent(url, userid, pw, compid);
    def lastcompid = 0;
    def xpos = 100;
    def ypos = 100;

    def data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/application/" + appid);
    
    if (data == null)
      return [-1, "", -1];

    if (data.success)
    {
      def complist = data.result.components;
      lastcompid = data.result.lastcompver;

      if (complist != null)
      {
       for (comp in complist)
       {
         def app_basecompid = getBaseComponent(url, userid, pw, comp.id);
         if (app_basecompid == basecompid)
          replaceCompId = comp.id;

         if (comp.id == lastcompid)
         {
          xpos = comp.xpos;
          ypos = comp.ypos + 100; 
         }
       }
      }         
    }

    if (replaceCompId >= 0)
    {
     data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/replace/" + appid + "/" + replaceCompId + "/" + compid);
    }
    else
    {
     assignComp2App(url, userid, pw, appid, compid, lastcompid, xpos, ypos);
    }  
   }

 /**
    * Assign Components to Application Version  
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param appname Text the application name
    * @param appversion Text the version of the application
    * @param Components String Array the components to assign to the application version
    * @return Boolean success/failure
    **/

  def assignComp2App(String url, String userid, String pw, Integer appid, Integer compid, Integer parent_compid, Integer xpos, Integer ypos)
  {
    def data;
    data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateAttrs?f=acd&a=" + appid + "&c=" + compid);
    data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateAttrs?f=acvm&a=" + appid + "&c=" + compid + "&xpos=" + xpos + "&ypos=" + ypos);
    data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/UpdateAttrs?f=cal&a=" + appid + "&fn=" + parent_compid + "&tn=" + compid);
  }
  /**
    * Update the component attrs 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid.  Use @credname to pull from Jenkins Credentials or set to "" to use default credential id "deployhub-creds"
    * @param pw Text the DeployHub password
    * @param compname Text the component name
    * @param compvariant Text the variant for the component
    * @param compversion Text the version of the variant
    * @param Attrs Map the key values pairs of attrs
    * @return Array with first element being the return code, second msg
    **/

  def updateComponentAttrs(String url, String userid, String pw, String compname, String compvariant, String compversion, Map Attrs)
  {
    compvariant = cleanName(compvariant);
    compversion = cleanName(compversion);

    if (compvariant == "" && compversion != null && compversion != "")
    {
      compvariant = compversion;
      compversion = null;  
    }

    def Component = "";

    if (compvariant != null && compvariant != "" && compversion != null && compversion != "")
        Component = compname + ";" + compvariant + ";" + compversion;
    else if (compvariant != null && compvariant != "")
        Component = compname + ";" + compvariant;
      else
        Component = compname;

    // Get compId    
    def data = getComponent(url, userid, pw, compname, compvariant, compversion);
    def compid = data[0];

    if (compid < 0)
      return;

    def count = 0;
    def i = 0;
    def attr_str = "";

    Attrs.eachWithIndex
    {
      key,value,index ->
      if (value == null)
        value = ""

      if (count == 0)
        attr_str = attr_str + "name=" + enc(key) + "&value=" + enc(value);
      else
        attr_str = attr_str + "&name" + count + "=" + enc(key) + "&value" + count + "=" + enc(value);

      count = count + 1;
    }

    if (attr_str.length() > 0)
    {
      // Update Attrs for component
      data = doGetHttpRequestWithJson(userid, pw, "${url}/dmadminweb/API/setvar/component/" + compid + "?" + attr_str);
      if (data.size() == 0)
        return [false, "Could not update attributes on '" + Component + "'"];
      else
        return [true, data, "${url}/dmadminweb/API/setvar/component/" + compid + "?" + attr_str];
    }
    else
      return [false, "No attributes to update on '" + Component + "'"];
  }
}
