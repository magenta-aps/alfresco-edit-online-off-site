function main()
{
  // Get the filter parameters
  var nameFilter = args["nf"];
  var sitePreset = args["spf"];
  var sizeString = args["size"];

  // Get the list of sites
  var sites = siteService.findSites(nameFilter, sizeString != null ? parseInt(sizeString) : -1);

  // ADDITION
  // Filter out the edit online default site
  sites = sites.filter(function (site) {return site.shortName != 'eods';});
  // END ADDITION

  model.sites = sites;
  model.roles = (args["roles"] !== null ? args["roles"] : "managers");
}

main();