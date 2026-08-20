package com.example.smartcanteen.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OperationalCategoryTest {

    @Test
    void exposes_fixed_ingredient_and_dish_category_labels() {
        assertThat(IngredientCategory.labels()).containsExactly(
                "蔬菜", "肉禽", "蛋奶", "水产", "主食", "豆制品", "调味品", "干货", "水果", "半成品", "其他");
        assertThat(DishCategory.labels()).containsExactly(
                "主食", "荤菜", "素菜", "荤素搭配", "汤羹", "炒菜", "小吃", "饮品", "早餐", "其他");
    }

    @Test
    void rejects_non_canonical_category_labels() {
        assertThatThrownBy(() -> IngredientCategory.require("绿叶菜"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported ingredient category: 绿叶菜");
        assertThatThrownBy(() -> DishCategory.require("热菜"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported dish category: 热菜");
    }
}
