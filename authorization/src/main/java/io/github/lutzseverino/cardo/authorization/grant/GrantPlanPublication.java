package io.github.lutzseverino.cardo.authorization.grant;

sealed interface GrantPlanPublication permits GrantPlan, StagedGrantPlan {}
