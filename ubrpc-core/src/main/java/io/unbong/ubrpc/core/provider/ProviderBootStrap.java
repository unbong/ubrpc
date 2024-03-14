package io.unbong.ubrpc.core.provider;

import io.unbong.ubrpc.core.annotation.UbProvider;
import io.unbong.ubrpc.core.api.RpcRequest;
import io.unbong.ubrpc.core.api.RpcResponse;
import io.unbong.ubrpc.core.meta.ProviderMeta;
import io.unbong.ubrpc.core.util.MethodUtil;
import io.unbong.ubrpc.core.util.TypeUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * 3
 *  1 将签名与方法保存起来
 *
 * @author <a href="ecunbong@gmail.com">unbong</a>
 */
public class ProviderBootStrap implements ApplicationContextAware {

    ApplicationContext _applicationContext ;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        _applicationContext = applicationContext;
    }

    // 为每个服务创建多值的方法元数据
    private MultiValueMap<String, ProviderMeta> skeleton = new LinkedMultiValueMap<>();

    @PostConstruct
    public void start(){
        Map<String, Object> map =_applicationContext.getBeansWithAnnotation(UbProvider.class);
        map.values().forEach(v->{
            getInterface(v);
        });
    }

    private void getInterface(Object v) {

        // 实现多个接口时 将所有接口的方法元数据收集
        Class<?>[] itfers = v.getClass().getInterfaces();
        for (Class<?> itfer: itfers){
            Method[] methods = itfer.getMethods();
            for(Method m : methods){
                if (MethodUtil.checkLocalMethod(m)){
                    continue;
                }
                createProvider(itfer, v, m);
            }
        }
    }

    /**
     * 创建方法元数据
     * @param itfer
     * @param v
     * @param m
     */
    private void createProvider(Class<?> itfer, Object v, Method m) {
        ProviderMeta meta = new ProviderMeta();
        meta.setServiceImpl(v);
        meta.setMethodSign(MethodUtil.method(m));
        meta.setMethod(m);
        System.out.println();
        skeleton.add(itfer.getCanonicalName(), meta);

    }


    public RpcResponse invoke(RpcRequest request)
    {

        RpcResponse rpcResponse = new  RpcResponse();
        // 在skeleton中找到 对应的bean
//        Object bean = skeleton.get(request.getService());
        List<ProviderMeta> providerMetas = skeleton.get(request.getService());

        try {

            ProviderMeta meta = findProviderMeta(providerMetas, request.getMethodSign());
            Object[] args = processArgs(request.getArgs(), meta.getMethod().getParameterTypes());
            Object result = meta.getMethod().invoke(meta.getServiceImpl(),args);
            rpcResponse.setData(result);
            rpcResponse.setStatus(true);

        } catch (InvocationTargetException e) {
            rpcResponse.setException(new RuntimeException(e.getTargetException().getMessage()));
        } catch (IllegalAccessException e) {
            rpcResponse.setException(new RuntimeException(e.getMessage()));
        }

        return rpcResponse;

    }

    /**
     * 将参数数据转换为对应的类型
     * @param args
     * @param parameterTypes
     * @return
     */
    private Object[] processArgs(Object[] args, Class<?>[] parameterTypes) {

        if(args == null || args.length == 0) return args;
        Object[] actual = new Object[args.length];
        for(int i = 0; i<actual.length; i++){
            actual[i] = TypeUtils.cast(args[i], parameterTypes[i]);
        }
        return actual;
    }

    private ProviderMeta findProviderMeta(List<ProviderMeta> providerMetas, String methodSign) {
       Optional<ProviderMeta> metaOptional =  providerMetas.stream().filter(x->x.getMethodSign().equals(methodSign)).findFirst();
       return metaOptional.get();
    }

    private Method findMethod(Class<?> aClass, String methodName) {

        for(Method method: aClass.getMethods())
        {
            if(method.getName().equals(methodName)){
                return  method;
            }
        }
        return null;
    }

}
