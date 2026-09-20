import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

// Consume a message's record target once, including navigation within the same page.
export function useNotificationTarget(openRecord) {
  const route=useRoute(),router=useRouter()
  watch(()=>route.query.focus,async id=>{
    if(typeof id!=='string'||!/^\d{1,18}$/.test(id))return
    try{await openRecord({id})}
    catch{/* The request interceptor displays the business error. */}
    finally{
      if(route.query.focus===id){const {focus,...query}=route.query;await router.replace({query})}
    }
  },{immediate:true})
}
