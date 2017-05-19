package landmaster.landcraft.jei;

import landmaster.landcraft.*;
import landmaster.landcraft.api.*;
import landmaster.landcraft.config.*;
import landmaster.landcraft.container.*;
import landmaster.landcraft.content.*;
import mcjty.lib.jei.*;
import mezz.jei.api.*;
import mezz.jei.api.recipe.transfer.*;
import net.minecraft.item.*;

@JEIPlugin
public class LandCraftJEI extends BlankModPlugin {
	@SuppressWarnings("deprecation") // addRecipeHandlers needed for compatibility w/ 1.10
	@Override
	public void register(IModRegistry registry) {
		LandCraft.log.debug("Adding JEI integration for LandCraft");
		
		//final IIngredientRegistry ingredientRegistry = registry.getIngredientRegistry();
		final IJeiHelpers jeiHelpers = registry.getJeiHelpers();
		final IGuiHelper guiHelper = jeiHelpers.getGuiHelper();
		final IRecipeTransferRegistry recipeTransferRegistry = registry.getRecipeTransferRegistry();
		
		if (Config.breeder) {
			registry.addRecipeCategories(new BreederFeedstockCategory(guiHelper));
			registry.addRecipeHandlers(new RecipeHandlerBase<>(
					BreederFeedstock.OreMassTempTri.class,
					BreederFeedstockJEI::new, BreederFeedstockCategory.UID));
			
			JeiCompatTools.addRecipes(registry, BreederFeedstock.getOreMassTempTris());
			
			recipeTransferRegistry.addRecipeTransferHandler(ContTEBreeder.class, BreederFeedstockCategory.UID, 0, 1, 3, 36);
			
			registry.addRecipeCategoryCraftingItem(new ItemStack(LandCraftContent.breeder), BreederFeedstockCategory.UID);
		}
		
		if (Config.pot) {
			registry.addRecipeCategories(new PotRecipeCategory(guiHelper));
			registry.addRecipeHandlers(new RecipeHandlerBase<>(
					PotRecipes.RecipePOredict.class,
					PotOredictRecipeJEI::new, PotRecipeCategory.UID));
			
			JeiCompatTools.addRecipes(registry, PotRecipes.getRecipeList());
			
			recipeTransferRegistry.addRecipeTransferHandler(ContTEPot.class, PotRecipeCategory.UID, 0, 3, 4, 36);
			
			registry.addRecipeCategoryCraftingItem(new ItemStack(LandCraftContent.pot), PotRecipeCategory.UID);
		}
	}
}
