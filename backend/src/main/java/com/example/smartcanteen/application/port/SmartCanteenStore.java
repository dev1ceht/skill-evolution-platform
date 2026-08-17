package com.example.smartcanteen.application.port;

/**
 * Compatibility aggregate for the original application workflow.
 * New modules depend on the smaller ports they actually use.
 */
public interface SmartCanteenStore extends MenuStore, RecipeStore, InventoryStore {
}
