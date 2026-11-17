import {auditEquipmentCatalog,catalog} from '../src/equipment-data.mjs';

const audit=auditEquipmentCatalog();
if(audit.missing.length)throw new Error(`Official equipment names missing from search: ${audit.missing.slice(0,20).join(', ')}`);
if(catalog.items.some(item=>!item.id||!item.icon||!item.category||!item.name))throw new Error('Malformed catalog item');
if(new Set(catalog.items.map(item=>item.id)).size!==catalog.items.length)throw new Error('Duplicate catalog ID');
console.log(JSON.stringify(audit));
