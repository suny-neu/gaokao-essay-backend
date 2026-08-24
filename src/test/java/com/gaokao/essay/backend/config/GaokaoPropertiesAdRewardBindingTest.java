package com.gaokao.essay.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GaokaoPropertiesAdRewardBindingTest {

  @Test
  void shouldBindRequiredAdRewardDailyLimitAndCreditPerViewNames() {
    GaokaoProperties properties = new GaokaoProperties();
    Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
        "gaokao.membership.ad-reward.daily-limit", "3",
        "gaokao.membership.ad-reward.credit-per-view", "2"
    )));

    binder.bind("gaokao", Bindable.ofInstance(properties));

    assertEquals(3, properties.getMembership().getAdReward().getDailyLimit());
    assertEquals(2, properties.getMembership().getAdReward().getCreditPerView());
  }
}
