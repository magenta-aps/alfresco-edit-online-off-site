package dk.magenta.editonline;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.cmr.site.SiteVisibility;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.context.ApplicationEvent;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EditOnlineOffsiteBehaviour extends AbstractLifecycleBean
        implements NodeServicePolicies.OnCreateChildAssociationPolicy, NodeServicePolicies.OnDeleteChildAssociationPolicy {
    public static final String EDIT_ONLINE_SITE_SHORT_NAME = "eods";
    public static final String EDIT_ONLINE_SITE_TITLE = "Edit Online Default Site";

    protected NodeService nodeService;
    protected Repository repositoryHelper;
    protected FileFolderService fileFolderService;
    protected SiteService siteService;
    protected PolicyComponent policyComponent;
    protected TransactionService transactionService;
    protected PermissionService permissionService;

    public void init() {
        policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME,
                ContentModel.TYPE_FOLDER,
                ContentModel.ASSOC_CONTAINS,
                new JavaBehaviour(this, "onCreateChildAssociation", Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
        policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnDeleteChildAssociationPolicy.QNAME,
                ContentModel.TYPE_FOLDER,
                ContentModel.ASSOC_CONTAINS,
                new JavaBehaviour(this, "onDeleteChildAssociation", Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
    }

    @Override
    protected void onBootstrap(ApplicationEvent event) {
        runAsSystemInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>() {
            @Override
            public Object execute() throws Throwable {
                bootstrap();
                return null;
            }
        });
    }

    private void bootstrap() {
        if (!siteService.hasSite(EDIT_ONLINE_SITE_SHORT_NAME)) {
            String fullyAuthenticatedUser = AuthenticationUtil.getFullyAuthenticatedUser();
            AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
            SiteInfo site;
            try {
                site = siteService.createSite("site-dashboard",
                        EDIT_ONLINE_SITE_SHORT_NAME, EDIT_ONLINE_SITE_TITLE,
                        EDIT_ONLINE_SITE_TITLE, SiteVisibility.PUBLIC);
                siteService.createContainer(site.getShortName(), SiteService.DOCUMENT_LIBRARY, null, null);
            } finally {
                AuthenticationUtil.setFullyAuthenticatedUser(fullyAuthenticatedUser);
            }

            // Add sys:hidden aspect to site
            nodeService.addAspect(site.getNodeRef(), ContentModel.ASPECT_HIDDEN, null);

            // Allow everyone to read/write on the site.
            // We don't use the site groups, since you can't add
            // GROUP_EVERYONE to another group.
            permissionService.setPermission(site.getNodeRef(), "GROUP_EVERYONE", "SiteCollaborator", true);
            permissionService.setPermission(site.getNodeRef(), "GROUP_EVERYONE", "SiteConsumer", true);
            permissionService.setPermission(site.getNodeRef(), "GROUP_EVERYONE", "ReadPermissions", true);
        }
//        List<String> siteRoles = siteService.getSiteRoles(EDIT_ONLINE_SITE_SHORT_NAME);
//        String siteGroup = siteService.getSiteGroup(EDIT_ONLINE_SITE_SHORT_NAME);
//        if (!authorityService.authorityExists(siteGroup)) {
//            authorityService.createAuthority(AuthorityType.GROUP,
//                    authorityService.getShortName(siteGroup));
//        }
//        for (String siteRole : siteRoles) {
//            String siteRoleGroup = siteService.getSiteRoleGroup(EDIT_ONLINE_SITE_SHORT_NAME,
//                    siteRole);
//            // Create the authority groups for the site.
//            // This should satisfy the site-finder page.
//            if (!authorityService.authorityExists(siteRoleGroup)) {
//                String siteRoleGroupShortName = authorityService.getShortName(siteRoleGroup);
//                Set<String> shareZones = new HashSet<String>(2, 1.0f);
//                shareZones.add(AuthorityService.ZONE_APP_SHARE);
//                shareZones.add(AuthorityService.ZONE_AUTH_ALFRESCO);
//                authorityService.createAuthority(AuthorityType.GROUP,
//                        siteRoleGroupShortName, siteRoleGroupShortName, shareZones);
//            }
//        }

        NodeRef companyHome = repositoryHelper.getCompanyHome();
        List<FileInfo> folders = fileFolderService.listFolders(companyHome);
        for (FileInfo folder : folders) {
            addNodeToSite(folder.getNodeRef());
        }
    }

    private void addNodeToSite(final NodeRef nodeRef) {
        runAsSystemInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>() {
            @Override
            public Object execute() throws Throwable {
                if (nodeRef.equals(siteService.getSiteRoot())) {
                    // Don't allow the "Sites" folder to be added to the Edit Online
                    // site. That would create a cycle. Besides, it should
                    // already possible to "Edit Online" for those nodes.
                    return null;
                }

                NodeRef docLibNodeRef = getSiteDocLibNodeRef();
                String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
                // Check if node already exists on site
                if (nodeService.getChildByName(docLibNodeRef, ContentModel.ASSOC_CONTAINS, name) == null) {
                    nodeService.addChild(docLibNodeRef, nodeRef, ContentModel.ASSOC_CONTAINS,
                            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI,
                                    QName.createValidLocalName(name)));
                }
                return null;
            }
        });
    }

    private void removeNodeFromSite(final NodeRef nodeRef) {
        runAsSystemInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>() {
            @Override
            public Object execute() throws Throwable {
                NodeRef docLibNodeRef = getSiteDocLibNodeRef();
                nodeService.removeChild(docLibNodeRef, nodeRef);
                return null;
            }
        });
    }

    private NodeRef getSiteDocLibNodeRef() {
        return siteService.getContainer(EDIT_ONLINE_SITE_SHORT_NAME, SiteService.DOCUMENT_LIBRARY);
    }

    @Override
    protected void onShutdown(ApplicationEvent event) {

    }

    @Override
    public void onCreateChildAssociation(ChildAssociationRef childAssocRef, boolean isNewNode) {
        if (nodeService.exists(childAssocRef.getChildRef()) && childAssocRef.getParentRef().equals(repositoryHelper.getCompanyHome())) {
            addNodeToSite(childAssocRef.getChildRef());
        }
    }

    @Override
    public void onDeleteChildAssociation(ChildAssociationRef childAssocRef) {
        if (nodeService.exists(childAssocRef.getChildRef()) && childAssocRef.getParentRef().equals(repositoryHelper.getCompanyHome())) {
            removeNodeFromSite(childAssocRef.getChildRef());
        }
    }

    private <T> T runAsSystemInTransaction(final RetryingTransactionHelper.RetryingTransactionCallback<T> callback) {
        return AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<T>() {
            @Override
            public T doWork() throws Exception {
                return transactionService.getRetryingTransactionHelper().doInTransaction(callback);
            }
        });
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setRepositoryHelper(Repository repositoryHelper) {
        this.repositoryHelper = repositoryHelper;
    }

    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }

    public void setSiteService(SiteService siteService) {
        this.siteService = siteService;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }


}
