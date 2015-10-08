edit-online-off-site
====================

This module makes it possible to perform the "Edit Online" action on files outside of sites.

This means, it is possible to perform "Edit Online" from within "My Files" or "Shared Files", as well as any other repository path.

It does this by creating a site called "eods", which everyone is able to read/write to.
The folders beneath the root folder (Company Home), are each added as children of the "eods" site (with the exception of the "Sites" folder, as that would create a cycle).
This does not mean that anyone can read/write to any of the folders in the site, since ACLs are inherited based on the primary parent of the node, e.g. the original parent folder.
If a node is added or removed from the root, it will also be removed from the "eods" site.

Additionally, the "eods" site will be hidden from the site finder in Share, and hidden from WebDAV and CIFS (for example).

This project requires both a Repository and Share AMP file to be installed to work.

To install, run:

    mvn package

Then, copy the AMP files in edit-online-off-site-repo/target/ and edit-online-off-site-share/target/ to the "amps" and "amps_share" folders of an Alfresco installation, respectively.
