package org.swan.shard.mybatis.interceptor;

import java.sql.Connection;
import java.util.Properties;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.swan.shard.mybatis.annotation.MetaShard;
import org.swan.shard.mybatis.scripting.DefaultScriptEngine;
import org.swan.shard.mybatis.scripting.ScriptContext;
import org.swan.shard.mybatis.scripting.ScriptEngine;

/**
 * 
 * @author 刘飞 E-mail:liufei_it@126.com
 * @version 1.0
 * @since 2014年2月24日 下午5:46:22
 */
@Intercepts({ @Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class }) })
public class ShardingPolicyInterceptor implements Interceptor {
	private static final ObjectFactory OF = new DefaultObjectFactory();
	private static final ObjectWrapperFactory OWF = new DefaultObjectWrapperFactory();
	private static final ScriptEngine SE = new DefaultScriptEngine("JavaScript");
	
	public Object intercept(Invocation invocation) throws Throwable {
		try {
			return intercept0(invocation);
		} catch (Throwable e) {
			// ingore
		}
		return invocation.proceed();
	}

	public Object intercept0(Invocation invocation) throws Throwable {
//		org.apache.ibatis.executor.statement.RoutingStatementHandler
		Object target = invocation.getTarget();
		if (!StatementHandler.class.isInstance(target)) {
			return invocation.proceed();
		}
		MetaObject metasth = MetaObject.forObject(target, OF, OWF);
		while (metasth.hasGetter("h")) {
			Object object = metasth.getValue("h");
			metasth = MetaObject.forObject(object, OF, OWF);
		}
		while (metasth.hasGetter("target")) {
			Object object = metasth.getValue("target");
			metasth = MetaObject.forObject(object, OF, OWF);
		}
//		Configuration configuration = (Configuration) metasth.getValue("delegate.configuration");
//		RowBounds rowBounds = (RowBounds) metaStatementHandler.getValue("delegate.rowBounds");
//		ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, "table_name", String.class);
//      boundSql.getParameterMappings().add(builder.mode(ParameterMode.IN).build());
//      boundSql.setAdditionalParameter("table_name", "User");
//      metasth.getValue("delegate.boundSql.parameterObject")
		MappedStatement mappedStatement = (MappedStatement) metasth.getValue("delegate.mappedStatement");
		String id = mappedStatement.getId();
		String className = id.substring(0, id.lastIndexOf("."));
		Class<?> clazz = Class.forName(className);
		MetaShard metaShard = clazz.getAnnotation(MetaShard.class);
		if(metaShard == null) {
			return invocation.proceed();
		}
		BoundSql boundSql = (BoundSql) metasth.getValue("delegate.boundSql");
//		metasth.getValue("delegate.boundSql.sql")
		String sql = boundSql.getSql();
		Object parameterObject = boundSql.getParameterObject();
		ScriptContext context = new ScriptContext();
		MetaObject metap = MetaObject.forObject(parameterObject, OF, OWF);
		String[] getter = metap.getGetterNames();
		if(getter != null && getter.length > 0) {
			for (String name : getter) {
				context.put(name, metap.getValue(name));
			}
		}
		String tableName = metaShard.name() + "_" + SE.eval(metaShard.expression(), context);
		metasth.setValue("delegate.boundSql.sql", sql.replaceAll(":table_name", tableName));
		System.out.println("delegate.boundSql.sql : " + metasth.getValue("delegate.boundSql.sql"));
		return invocation.proceed();
	}

	@Override
	public Object plugin(Object target) {
		if (target instanceof StatementHandler) {
			return Plugin.wrap(target, this);
		}
		return target;
	}

	@Override
	public void setProperties(Properties properties) {

	}
}