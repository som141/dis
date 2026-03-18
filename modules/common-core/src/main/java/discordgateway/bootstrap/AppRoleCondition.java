package discordgateway.bootstrap;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

public class AppRoleCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnAppRole.class.getName());
        if (attributes == null) {
            return false;
        }

        AppRole currentRole = resolveRole(context.getEnvironment().getProperty("app.role", "all"));
        AppRole[] allowedRoles = (AppRole[]) attributes.get("value");
        if (allowedRoles == null || allowedRoles.length == 0) {
            return false;
        }

        for (AppRole allowedRole : allowedRoles) {
            if (allowedRole == currentRole) {
                return true;
            }
        }

        return false;
    }

    private AppRole resolveRole(String configured) {
        if (configured == null || configured.isBlank()) {
            return AppRole.ALL;
        }

        String normalized = configured.trim()
                .replace('-', '_')
                .toUpperCase();
        return AppRole.valueOf(normalized);
    }
}
