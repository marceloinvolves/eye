package br.com.eye;

import java.lang.reflect.Method;
import java.util.Date;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import br.com.eye.annotations.Sensor;
import br.com.eye.data.SonarData;
import br.com.eye.data.TypesData;

@Aspect
@Component
public class EyeAspect extends EyeAbstract {

	private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Value("${spring.application.name:}")
    private String appName;

    @Value("${spring.application.version:}")
    private String appVersion;

    @Value("${eye.url:}")
    private String eyeLink;

    @Value("${eye.disabled:}")
    private String disabled;

    @Autowired
    private ApplicationContext context;
    
	@Around("@annotation(br.com.eye.annotations.Sensor) && execution(* *(..))")
	public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
		
		LOGGER.debug("Método interceptado pelo Sonar.");

        if("false".equals(disabled)){
            LOGGER.debug("ignorado interceptação");
            return joinPoint.proceed();
        }

		long tempoInicial = System.currentTimeMillis();

		Object returnObject = null;

        // metodo anotado
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        LOGGER.debug("Metodo: "+method.getName());
        
        Sensor sensor = method.getAnnotation(Sensor.class);
        
        // iniciando sonar
        SonarData sonarData = new SonarData();
        
        if( sensor.type() == TypesData.DEPENDENCY ){
        	
        	String microservice = joinPoint.getArgs()[0].toString();
        	String link = joinPoint.getArgs()[1].toString();
        	
        	sonarData.setmOrigin(appName);
        	sonarData.setmDestiny(microservice);
        	sonarData.setmLink(link);
        }
        
        
        sonarData.setBuildTimestamp(DATE_IN.format(new Date()));
        
        sonarData.setDescription(sensor.description());
        sonarData.setTags(sensor.tags());
        sonarData.setType(sensor.type().getValue());
        sonarData.setTimeInit(new Date().getTime());
        sonarData.setServer(appName);
        sonarData.setVersion(appVersion);
        
        IdentifyClient identifyClient = getIdentifyClient();
        
        if( identifyClient != null ){
        	sonarData.setClient(identifyClient.client());
        }
        
        try {
            returnObject = joinPoint.proceed();
        } catch (Throwable throwable) {
        	
        	LOGGER.debug("Erro captado pelo sonar: "+throwable.getMessage());

            StringBuilder messageStackTrace = new StringBuilder();
            if( throwable.getStackTrace() != null ){
                for(StackTraceElement item : throwable.getStackTrace()){
                    messageStackTrace.append(item.toString()).append("<br>");
                }
            }

            sonarData.setMessageStackTrace(messageStackTrace.toString());
        	sonarData.setError(true);
        	sonarData.setMessageError(throwable.getMessage());
        	sonarData.setException(throwable.getClass().getSimpleName());
            throw throwable;
        }
        finally {
        	
        	LOGGER.debug("Finalizando Sonar..");
        	
            long tempoFinal = System.currentTimeMillis() - tempoInicial;
            sonarData.setTimeExec(tempoFinal);

            new Thread(new Send(sonarData, eyeLink)).start();
        }
        
        return returnObject;
    }

	private IdentifyClient getIdentifyClient(){
		try{
			return context.getBean(IdentifyClient.class);
		}catch(NoSuchBeanDefinitionException ex){
			return null;
		}
	}
	
}
