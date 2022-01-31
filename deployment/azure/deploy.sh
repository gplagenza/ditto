echo "Step 1. Creating an explicit service principal...starting"

service_principal=`az ad sp create-for-rbac --name http://dittoServicePrincipal --skip-assignment --output tsv`
app_id_principal=`echo $service_principal|cut -f1 -d ' '`
password_principal=`echo $service_principal|cut -f4 -d ' '`
object_id_principal=`az ad sp show --id $app_id_principal --query objectId --output tsv`
echo "app_id_principal=" $app_id_principal
echo "object_id_principal=" $object_id_principal
echo "password_principal=" $password_principal
echo "Step 1. Creating an explicit service principal...done"

echo "Step 2. Creating resource group...starting"

resourcegroup_name=armtest
az group create --name $resourcegroup_name --location "westeurope"

echo "Step 2. Creating resource group...done"

echo "Step 3. Creating ARM deployment...starting"

unique_solution_prefix=myprefix
az deployment group create --name DittoBasicInfrastructure --resource-group $resourcegroup_name --template-file arm/dittoInfrastructureDeployment.json --parameters uniqueSolutionPrefix=$unique_solution_prefix servicePrincipalObjectId=$object_id_principal servicePrincipalClientId=$app_id_principal servicePrincipalClientSecret=$password_principal

aks_cluster_name=`az deployment group show --name DittoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.aksClusterName.value -o tsv`
ip_address=`az deployment group show --name DittoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.publicIPAddress.value -o tsv`
cosmos_mongodb_primary_master_key=`az deployment group show --name DittoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.cosmosPrimaryMasterKey.value -o tsv`
cosmos_account_name=`az deployment group show --name DittoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.cosmosAccountName.value -o tsv`
public_fqdn=`az deployment group show --name DittoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.publicIPFQDN.value -o tsv`

echo "aks_cluster_name=" $aks_cluster_name
echo "cosmos_account_name=" $cosmos_account_name
echo "public_fqdn=" $public_fqdn

echo "Step 3. Creating ARM deployment...done"

echo "Step 4. Setting AKS cluster...starting"
az aks get-credentials --resource-group $resourcegroup_name --name $aks_cluster_name
echo "Step 4. Setting AKS cluster...done"

echo "Step 5. Deploying helm on cluster...starting"
kubectl apply -f helm-rbac.yaml
--helm init --service-account tiller
echo "Step 5. Deploying helm on cluster...done"

echo "Step 6. Preparing the k8s environment and chart for deployment....starting"
k8s_namespace=dittons
kubectl create namespace $k8s_namespace
helm dependency update ../helm/eclipse-ditto/
echo "Step 6. Preparing the k8s environment and chart for deployment....done"

echo "Step 7. Install Ditto with embedded MongoDB and persistent storage as part of the helm release....starting"
helm upgrade ditto ../helm/eclipse-ditto/ --namespace $k8s_namespace --set service.type=LoadBalancer,service.loadBalancerIP.enabled=true,service.loadBalancerIP.address=$ip_address,service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-resource-group"=$resourcegroup_name,mongodb.persistence.enabled=true,mongodb.persistence.storageClass=managed-premium-retain --wait --install
echo "Step 7. Install Ditto with embedded MongoDB and persistent storage as part of the helm release....done"

echo"Have fun with Eclipse Ditto on Microsoft Azure!"
read -p "Enter to exit " -n 1 -r


