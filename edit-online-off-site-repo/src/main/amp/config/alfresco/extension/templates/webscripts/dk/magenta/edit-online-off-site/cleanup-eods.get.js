// locate folder by path
// NOTE: only supports path beneath company home, not from root
var site = siteService.getSite("eods");
var docLib = site.getContainer("documentLibrary");

//var ctx = Packages.org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
//var nodeService = ctx.getBean("nodeService");
//var jsonUtils = new Packages.org.springframework.extensions.webscripts.json.JSONUtils();

var out = "";
for (var i = 0; i < docLib.children.length; i++) {
  out += docLib.children[i].name + "    \n";
  docLib.removeNode(docLib.children[i]);
}
site.deleteSite();
model.out = out;
