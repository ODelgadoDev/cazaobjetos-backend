from pathlib import Path
import re,json
root=Path(__file__).resolve().parents[1];java=root/'src/main/java/mx/utch/cazaobjetos';schemas={}
def ref(n):return {'$ref':'#/components/schemas/'+n}
def typ(t):
 t=t.strip()
 if t.startswith('List<'):return {'type':'array','items':typ(t[5:-1])}
 if t in ['String','UUID','Instant','LocalDate']:
  d={'type':'string'}
  if t!='String':d['format']={'UUID':'uuid','Instant':'date-time','LocalDate':'date'}[t]
  return d
 if t in ['int','Integer','long','Long']:return {'type':'integer','format':'int64' if t in ['long','Long'] else 'int32'}
 if t in ['boolean','Boolean']:return {'type':'boolean'}
 return ref(t)
for p in (java/'enums').glob('*.java'):
 m=re.search(r'enum\s+(\w+)\s*\{([^}]+)',p.read_text())
 if m:schemas[m[1]]={'type':'string','enum':[x.strip() for x in m[2].split(',')]}
for p in (java/'dto').glob('*.java'):
 s=re.sub(r'@\w+(?:\([^)]*\))?','',p.read_text());m=re.search(r'record\s+(\w+)\s*\((.*?)\)\s*\{',s,re.S)
 if not m:continue
 props={}
 for field in m[2].split(','):
  t,n=field.strip().rsplit(None,1);props[n]=typ(t)
 schemas[m[1]]={'type':'object','properties':props}
for name in ['FoundItemResponseDto','LostReportResponseDto','ClaimResponseDto','DirectoryUserDto','PhotoResponseDto','ClaimActivityDto','NotificationResponseDto']:
 schemas['Page'+name]={'type':'object','required':['items','total','page','pageSize','totalPages','hasNext','hasPrevious'],'properties':{'items':{'type':'array','items':ref(name)},'total':typ('long'),'page':typ('int'),'pageSize':typ('int'),'totalPages':typ('int'),'hasNext':typ('boolean'),'hasPrevious':typ('boolean')}}
for name in ['ReportRequestDto','RegisterRequestDto','EmailRequestDto','VerifyEmailRequestDto','ResetPasswordRequestDto','ChangePasswordRequestDto','DelegateRequestDto','ImportPhotoRequestDto']:
 schemas[name]['required']=list(schemas[name]['properties'])
schemas['ClaimRequestDto']['required']=['privateDescription']
schemas['ImportPhotoRequestDto']['required']=['url']
schemas['ImportPhotoRequestDto']['properties']['url'].update(format='uri',maxLength=2048,description='Enlace HTTPS directo de una imagen pública. Puerto 443.')
schemas['ImportPhotoRequestDto']['properties']['referenceImage']['default']=False
for name in ['RegisterRequestDto','ResetPasswordRequestDto','ChangePasswordRequestDto']:
 for field in ['password','newPassword']:
  if field in schemas[name]['properties']:schemas[name]['properties'][field].update(minLength=12,maxLength=72,writeOnly=True,description='Máximo 72 bytes UTF-8; no se recorta.')
for field,maximum in [('title',120),('description',2000),('location',200)]:schemas['ReportRequestDto']['properties'][field].update(minLength=1,maxLength=maximum)
schemas['ReportRequestDto']['properties']['occurredOn']['description']='Fecha de hoy o anterior.'
schemas['ClaimRequestDto']['properties']['privateDescription'].update(minLength=1,maxLength=2000)
for name,fields in {'ClaimRequestDto':['lostReportId'],'ClaimResponseDto':['lostReportId','acceptedBy','acceptedAt','completedAt'],'FoundItemResponseDto':['officeMarkedBy','officeMarkedAt'],'LostReportResponseDto':['recoveredAt']}.items():
 for f in fields:schemas[name]['properties'][f]['nullable']=True
for field,maximum in [('alias',60),('classroom',80),('groupName',80),('course',120),('degree',160),('building',80)]:
 schemas['UserResponseDto']['properties'][field].update(nullable=True,maxLength=maximum,description='Dato opcional del perfil de la cuenta autenticada. Sin valor: null.')
schemas['UserResponseDto']['properties']['studentNumber'].update(nullable=True,readOnly=True,pattern='^[0-9]{10}$',description='Solo alumnos cuyo correo coincida con a + 10 dígitos @utch.edu.mx; no acredita inscripción.')
schemas['UserResponseDto']['properties']['avatarVersion'].update(nullable=True,readOnly=True,description='Versión de la foto privada en /auth/me/photo; null si no hay foto.')
schemas['UpdateProfileRequestDto']['required']=['displayName']
schemas['UpdateProfileRequestDto']['additionalProperties']=False
for field,maximum in [('displayName',120),('alias',60),('classroom',80),('groupName',80),('course',120),('degree',160),('building',80)]:
 schemas['UpdateProfileRequestDto']['properties'][field].update(maxLength=maximum)
 if field!='displayName':schemas['UpdateProfileRequestDto']['properties'][field]['nullable']=True
schemas['UpdateProfileRequestDto']['properties']['displayName']['minLength']=3
schemas['ClaimCommentRequestDto']['required']=['body']
schemas['ClaimCommentRequestDto']['properties']['body'].update(minLength=1,maxLength=2000)
schemas['ClaimDecisionRequestDto']['properties']['reason'].update(nullable=True,maxLength=2000)
schemas['CustodyRequestDto']['required']=['custody']
schemas['CustodyRequestDto']['properties']['teacherName'].update(nullable=True,maxLength=120,description='Referencia verbal, obligatoria para WITH_TEACHER; no asigna permisos ni envía avisos al docente.')
schemas['CustodyRequestDto']['properties']['location'].update(nullable=True,maxLength=200)
for field in ['teacherName','custodyLocation','custodyMarkedBy','custodyMarkedAt','sourceLostReportId']:schemas['FoundItemResponseDto']['properties'][field]['nullable']=True
for field in ['actorId','detailsJson','evidenceId']:schemas['ClaimActivityDto']['properties'][field]['nullable']=True
schemas['NotificationResponseDto']['properties']['readAt']['nullable']=True
schemas['FoundPublicationRequestDto']['required']=['report','custody']
schemas['FoundPublicationRequestDto']['properties']['sourceLostReportId'].update(nullable=True,description='Extravío de origen activo, publicado por otra persona; coincidencia posible, sin crear ni cerrar reclamos automáticamente.')
schemas['PushConfigDto']['properties']['applicationServerKey'].update(nullable=True,description='Clave pública VAPID P-256, nunca la privada.')
schemas['PushSubscriptionRequestDto']['required']=['expectedUserId','endpoint','keys']
schemas['PushSubscriptionRequestDto']['properties']['endpoint'].update(format='uri',maxLength=2048,writeOnly=True,description='Endpoint HTTPS del servicio push permitido; no se aceptan destinos arbitrarios.')
schemas['PushKeysDto']['required']=['p256dh','auth']
for field in ['p256dh','auth']:schemas['PushKeysDto']['properties'][field]['writeOnly']=True
schemas['PushSubscriptionResponseDto']['properties']['bindingToken'].update(description='Vinculación opaca al dispositivo; guardar solo en el service worker de esta cuenta.')
schemas['PushPreferenceRequestDto']['required']=['dailyReminders']
schemas['PushPreferenceRequestDto']['properties']['dailyReminders'].update(minimum=0,maximum=2)
schemas['PushTestRequestDto']['required']=['subscriptionId']
paths={}
def param(name,schema,where='query',required=False):return {'name':name,'in':where,'required':required,'schema':schema}
def operation(path,method,summary,response=None,code=200,body=None,media='application/json',public=False,queries=None,idempotent=False):
 params=[param(name,typ('UUID'),'path',True) for name in re.findall(r'{(\w+)}',path)]
 params+=queries or []
 if method in ['post','put','delete']:params.append(param('X-CSRF-TOKEN',{'type':'string'},'header',True))
 if idempotent:params.append(param('Idempotency-Key',{'type':'string','pattern':'^[A-Za-z0-9._:-]{8,100}$','description':'Misma clave, usuario, ruta y datos: mismo ID. Datos diferentes: 409.'},'header'))
 result={'description':'Operación correcta'}
 if response:result['content']={'application/json':{'schema':ref(response) if isinstance(response,str) else response}}
 errors={str(c):{'description':text} for c,text in [(400,'Entrada inválida'),(401,'Sesión requerida o revocada'),(403,'Sin permiso o CSRF inválido'),(404,'Recurso inexistente o no visible'),(409,'Estado incompatible o clave reutilizada con otros datos'),(429,'Límite de intentos; Retry-After: 60')]}
 op={'summary':summary,'operationId':method+'_'+re.sub(r'[^a-zA-Z0-9]+','_',path).strip('_'),'responses':{str(code):result,**errors}}
 if public:op['security']=[]
 if params:op['parameters']=params
 if body:op['requestBody']={'required':True,'content':{media:{'schema':ref(body) if isinstance(body,str) else body}}}
 paths.setdefault(path,{})[method]=op
 return op
page=[param('page',{'type':'integer','minimum':0,'maximum':10000,'default':0}),param('pageSize',{'type':'integer','minimum':1,'maximum':100,'default':20})]
operation('/notifications','get','Mis avisos visibles sin marcarlos como leídos','PageNotificationResponseDto',queries=page+[param('filter',{'allOf':[ref('NotificationFilter')],'default':'ALL'})])
operation('/notifications/summary','get','Contadores personales de pendientes y total visible','NotificationSummaryDto')
operation('/notifications/{id}/read','post','Marcar solo el aviso pulsado como leído','NotificationResponseDto')
operation('/notifications/clear-read','post','Retirar únicamente avisos ya leídos sin borrar su historial','NotificationClearDto')
operation('/lost-reports/{id}/matches','get','Posibles hallazgos del reporte, solo para su dueño','PageFoundItemResponseDto',queries=page)
publication=operation('/found-items/publish','post','Publicar hallazgo completo con foto, resguardo y posible extravío de origen en una sola transacción','IdResponseDto',201,{'type':'object','required':['request','file'],'properties':{'request':ref('FoundPublicationRequestDto'),'file':{'type':'string','format':'binary'}}},media='multipart/form-data',idempotent=True)
publication['requestBody']['content']['multipart/form-data']['encoding']={'request':{'contentType':'application/json'}}
publication['responses'].update({'413':{'description':'Foto de más de 5 MiB'},'415':{'description':'Foto no válida como JPEG/PNG'}})
operation('/push/config','get','Configuración pública de Web Push para la sesión','PushConfigDto')
operation('/push/subscriptions','post','Vincular el dispositivo a la cuenta de la sesión','PushSubscriptionResponseDto',200,'PushSubscriptionRequestDto')
operation('/push/subscriptions/{id}','delete','Desactivar solo el dispositivo autorizado',code=204)
operation('/push/test','post','Programar una prueba en este dispositivo, máximo una por minuto','IdResponseDto',202,'PushTestRequestDto')
operation('/push/preferences','get','Preferencias de recordatorios del usuario','PushPreferenceDto')
operation('/push/preferences','put','Elegir cero, uno o dos recordatorios del servidor','PushPreferenceDto',200,'PushPreferenceRequestDto')
for route,resp,status in [('found-items','FoundItemResponseDto','FoundStatus'),('lost-reports','LostReportResponseDto','LostStatus')]:
 base='/'+route
 queries=page+[param('q',{'type':'string','maxLength':200}),param('location',{'type':'string','maxLength':200}),param('category',ref('Category')),param('status',ref(status)),param('dateFrom',typ('LocalDate')),param('dateTo',typ('LocalDate')),param('sort',{'allOf':[ref('CatalogSort')],'default':'RECENT'})]
 if route=='found-items':queries+=[param('custody',ref('CustodyStatus'))]
 for suffix in ['', '/mine']:operation(base+suffix,'get','Catálogo personal' if suffix else 'Catálogo general sin retirados','Page'+resp,queries=queries)
 operation(base,'post','Crear publicación','IdResponseDto',201,'ReportRequestDto',idempotent=True)
 operation(base+'/{id}','get','Detalle visible',resp)
 operation(base+'/{id}','put','Editar publicación',code=204,body='ReportRequestDto')
 operation(base+'/{id}/withdraw','post','Retirar conservando historial',code=204)
 if route=='found-items':
  operation(base+'/{id}/office','post','Registrar resguardo en oficina',code=204)
  operation(base+'/{id}/delegate','post','Delegar responsable',code=204,body='DelegateRequestDto')
  claim=operation(base+'/{id}/claims','post','Solicitar reclamo con evidencias opcionales guardadas atómicamente','IdResponseDto',201,'ClaimRequestDto',idempotent=True)
  claim['requestBody']['content']['multipart/form-data']={'schema':{'type':'object','required':['request'],'properties':{'request':ref('ClaimRequestDto'),'files':{'type':'array','maxItems':5,'items':{'type':'string','format':'binary'}}}},'encoding':{'request':{'contentType':'application/json'}}}
  claim['responses'].update({'413':{'description':'Más de 5 MiB por imagen / 26 MiB por petición'},'415':{'description':'Solo evidencias JPEG/PNG'}})
  operation(base+'/{id}/custody','put','Actualizar resguardo sin transferir gestión; oficina no se revierte',code=204,body='CustodyRequestDto')
 else:operation(base+'/{id}/recover','post','Cerrar extravío recuperado externamente',code=204)
 photos=base+'/{parent}/photos'
 operation(photos,'get','Metadatos de fotos',{'type':'array','items':ref('PhotoResponseDto')})
 upload=operation(photos,'post','Subir JPEG/PNG: máximo 3 fotos, 5 MiB cada una','PhotoResponseDto',201,{'type':'object','required':['file'],'properties':{'file':{'type':'string','format':'binary'}}},media='multipart/form-data')
 upload['responses'].update({'413':{'description':'Más de 5 MiB por archivo / 26 MiB por petición'},'415':{'description':'Solo JPEG/PNG'}})
 if route=='lost-reports':
  operation(photos+'/import','post','Importar copia JPEG/PNG desde HTTPS público','PhotoResponseDto',201,'ImportPhotoRequestDto')
  upload['requestBody']['content']['multipart/form-data']['schema']['properties']['referenceImage']={'type':'boolean','default':False}
 op=operation(photos+'/{id}','get','Contenido de imagen')
 op['responses']['200']['content']={t:{'schema':{'type':'string','format':'binary'}} for t in ['image/png','image/jpeg']}
 operation(photos+'/{id}','delete','Eliminar foto',code=204)
operation('/claims','get','Listado anterior: array de 20',{'type':'array','items':ref('ClaimResponseDto')},queries=[page[0]])
operation('/claims/search','get','Reclamos del solicitante o responsable actual','PageClaimResponseDto',queries=page+[param('view',ref('ClaimView')),param('status',ref('ClaimStatus')),param('foundItemId',typ('UUID'))])
operation('/claims/{id}','get','Detalle privado y contactos','ClaimDetailDto')
for action in ['accept','reject','cancel','complete']:
 op=operation('/claims/{id}/'+action,'post','Acción de reclamo: '+action,code=204,body='ClaimDecisionRequestDto');op['requestBody']['required']=False
operation('/claims/{claimId}/activity','get','Historial privado, más reciente primero; conserva hechos y comentarios','PageClaimActivityDto',queries=page)
operation('/claims/{claimId}/comments','post','Publicar comentario privado mientras el reclamo siga abierto','ClaimActivityDto',201,'ClaimCommentRequestDto',idempotent=True)
operation('/claims/{claimId}/evidence','get','Evidencias privadas, conservadas tras el cierre',{'type':'array','items':ref('ClaimEvidenceDto')})
op=operation('/claims/{claimId}/evidence','post','Agregar evidencia privada JPEG/PNG; máximo 5 por reclamo','ClaimEvidenceDto',201,body={'type':'object','required':['file'],'properties':{'file':{'type':'string','format':'binary'}}},media='multipart/form-data',idempotent=True)
op['responses'].update({'413':{'description':'Más de 5 MiB'},'415':{'description':'Solo JPEG/PNG'}})
op=operation('/claims/{claimId}/evidence/{evidenceId}','get','Contenido privado de evidencia; no-store y nosniff')
op['responses']['200']['content']={kind:{'schema':{'type':'string','format':'binary'}} for kind in ['image/jpeg','image/png']}
operation('/responsibles','get','Docentes y secretaria habilitados','PageDirectoryUserDto',queries=page+[param('q',{'type':'string','maxLength':200}),param('role',{'type':'string','enum':['TEACHER','SECRETARY']})])
operation('/auth/csrf','get','Token CSRF y cookie anónima','CsrfResponseDto',public=True)
operation('/auth/me','get','Perfil de sesión','UserResponseDto')
operation('/auth/me','put','Guardar nombre, alias e información académica; los opcionales ausentes se vacían','UserResponseDto',body='UpdateProfileRequestDto')
photo=operation('/auth/me/photo','get','Foto privada de la cuenta autenticada; JPEG 512 × 512, sin caché')
photo['responses']['200']['content']={'image/jpeg':{'schema':{'type':'string','format':'binary'}}}
photo=operation('/auth/me/photo','put','Reemplazar foto de perfil; JPEG/PNG de hasta 5 MiB, recorte central','UserResponseDto',body={'type':'object','required':['file'],'properties':{'file':{'type':'string','format':'binary'}}},media='multipart/form-data')
photo['responses'].update({'413':{'description':'Más de 5 MiB por archivo / 26 MiB por petición'},'415':{'description':'Solo JPEG/PNG'}})
operation('/auth/me/photo','delete','Quitar foto; devuelve el perfil actualizado','UserResponseDto')
operation('/auth/login','post','Iniciar sesión; renovar CSRF después',code=204,body={'type':'object','required':['username','password'],'properties':{'username':{'type':'string','format':'email'},'password':{'type':'string','format':'password'}}},media='application/x-www-form-urlencoded',public=True)
operation('/auth/logout','post','Cerrar sesión',code=204,public=True)
for action,dto,code in [('register','RegisterRequestDto',202),('verify-email','VerifyEmailRequestDto',204),('resend-verification','EmailRequestDto',202),('forgot-password','EmailRequestDto',202),('reset-password','ResetPasswordRequestDto',204),('change-password','ChangePasswordRequestDto',204)]:operation('/auth/'+action,'post',action,code=code,body=dto,public=action!='change-password')
op=operation('/health','get','Disponibilidad de API y PostgreSQL',{'type':'object','properties':{'status':{'type':'string','enum':['UP','DOWN']}}},public=True);op['responses']['503']={'description':'Base de datos no disponible'}
operation('/openapi.json','get','Contrato OpenAPI',{'type':'object'},public=True)
spec={'openapi':'3.0.3','info':{'title':'CazaObjetos UTCH API','version':'0.1.0','description':'MVP edificio I. Cookie de sesión + CSRF. Nunca usar Bearer. page inicia en 0. Ver docs/integration.md para permisos, reintentos y despliegue.'},'servers':[{'url':'/api/v1'}],'security':[{'sessionCookie':[]}],'paths':paths,'components':{'securitySchemes':{'sessionCookie':{'type':'apiKey','in':'cookie','name':'JSESSIONID'}},'schemas':schemas}}
(root/'src/main/resources/openapi.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n')
print(len(paths),'rutas;',sum(len(v) for v in paths.values()),'operaciones')
