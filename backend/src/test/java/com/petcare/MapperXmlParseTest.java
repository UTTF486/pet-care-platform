package com.petcare;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper XML 自检（不需要数据库）。
 * <p>
 * 作用：MyBatis 的 XML 是"字符串约定"，写错 resultType 类名、语句 id 拼错，
 * 都要等应用启动甚至调用时才报错。这个测试在编译期就把问题暴露出来。
 * <p>
 * 校验两件事：
 * 1. 9 个 XML 都能被 MyBatis 正确解析（resultType 类存在、标签合法、SQL 片段可引用）；
 * 2. Mapper 接口里每个方法都有对应的语句（防止写了方法忘了写 SQL）。
 * <p>
 * 运行：mvn test -Dtest=MapperXmlParseTest
 */
class MapperXmlParseTest {

    private static final String[] MAPPERS = {
            "UserMapper", "PetMapper", "CategoryMapper", "ServiceItemMapper", "ServicerMapper",
            "OrderMapper", "OrderLogMapper", "ReviewMapper", "MessageMapper"
    };

    @Test
    void allMapperXmlShouldParseAndMatchInterface() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);

        for (String mapper : MAPPERS) {
            String resource = "mapper/" + mapper + ".xml";
            try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        // 每个接口方法都必须有同名语句，否则运行时会抛 BindingException
        for (String mapper : MAPPERS) {
            Class<?> mapperInterface = Class.forName("com.petcare.mapper." + mapper);
            for (Method method : mapperInterface.getDeclaredMethods()) {
                String statementId = mapperInterface.getName() + "." + method.getName();
                assertTrue(configuration.hasStatement(statementId), "缺少 SQL 语句: " + statementId);
            }
        }
    }
}
