package landmaster.landcraft.tile;

import java.util.*;

import javax.annotation.*;

import gnu.trove.set.hash.*;
import landmaster.landcore.api.*;
import landmaster.landcraft.*;
import landmaster.landcraft.block.*;
import landmaster.landcraft.config.*;
import landmaster.landcraft.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.nbt.*;
import net.minecraft.server.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.relauncher.*;

public class TELandiaPortalMarker extends TileEntity implements ITickable {
	private final Set<UUID> already = new THashSet<>();
	
	static class ClRes {
		public @Nullable TELandiaPortalMarker tile;
		public BlockPos pos;
		
		public ClRes() {}
		public ClRes(@Nullable TELandiaPortalMarker tile, BlockPos pos) {
			this.tile = tile; this.pos = pos;
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		int[] int_arr = compound.getIntArray("Already");
		for (int i=0; i<int_arr.length; i+=4) {
			already.add(new UUID((((long)int_arr[i]) << 32) | (int_arr[i+1] & 0xFFFFFFFFL),
					(((long)int_arr[i+2]) << 32) | (int_arr[i+3] & 0xFFFFFFFFL)));
		}
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound = super.writeToNBT(compound);
		int[] int_arr = new int[already.size()*4];
		int i = 0;
		for (UUID uuid: already) {
			int_arr[i] = (int)(uuid.getMostSignificantBits() >> 32);
			int_arr[i+1] = (int)uuid.getMostSignificantBits();
			int_arr[i+2] = (int)(uuid.getLeastSignificantBits() >> 32);
			int_arr[i+3] = (int)uuid.getLeastSignificantBits();
			i += 4;
		}
		compound.setIntArray("Already", int_arr);
		return compound;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
		BlockPos posA = new BlockPos(pos.getX(), 0, pos.getZ());
		return new AxisAlignedBB(posA, pos.add(1, 1, 1));
	}
	
	@Override
	public void update() {
		if (getWorld().isRemote) return;
		if (getWorld().getBlockState(pos).getValue(BlockLandiaPortalMarker.ACTIVATED)) {
			List<Entity> ents = getWorld().getEntitiesWithinAABB(Entity.class, Utils.AABBfromVecs(
					portalLBound().subtract(0.2, 0, 0.2),
					new Vec3d(pos).addVector(0.5+0.2, -0.01, 0.5+0.2)));
			if (!ents.isEmpty()) {
				int dimID = getWorld().provider.getDimension() != Config.landiaDimensionID
						? Config.landiaDimensionID : 0;
				ClRes res = generateClearance(pos, dimID);
				Coord4D coord = new Coord4D(res.pos.getX(), res.pos.getY(), res.pos.getZ(), dimID);
				for (Entity ent: ents) {
					if (res.tile != null) {
						res.tile.already.add(ent.getUniqueID());
					}
					if (!already.contains(ent.getUniqueID())) {
						if (ent instanceof EntityPlayerMP) {
							Tools.teleportPlayerTo((EntityPlayerMP)ent, coord);
						} else {
							Tools.teleportEntityTo(ent, coord);
						}
					}
				}
			}
			for (Iterator<UUID> it = already.iterator(); it.hasNext(); ) {
				UUID uuid = it.next();
				if (!ents.stream().anyMatch(ent -> uuid.equals(ent.getUniqueID()))) {
					it.remove();
				}
			}
		}
	}
	
	public BlockPos getBottom() {
		Vec3d vec0 = new Vec3d(pos).addVector(0.5, -0.01, 0.5);
		Vec3d vec1 = new Vec3d(pos.getX()+0.5, 0, pos.getZ()+0.5);
		
		BlockPos res = new BlockPos(pos.getX(), 0, pos.getZ());
		
		RayTraceResult rtr = getWorld().rayTraceBlocks(vec0, vec1);
		
		if (rtr != null) res = rtr.getBlockPos();
		return res;
	}
	
	public Vec3d portalLBound() {
		Vec3d vec0 = new Vec3d(pos).addVector(0.5, -0.01, 0.5);
		Vec3d vec1 = new Vec3d(pos.getX()+0.5, 0, pos.getZ()+0.5);
		
		RayTraceResult rtr = getWorld().rayTraceBlocks(vec0, vec1);
		
		if (rtr != null) vec1 = rtr.hitVec;
		return vec1;
	}
	
	//=============================================================================
	
	static ClRes searchPortal(World world, BlockPos pos) {
		List<TELandiaPortalMarker> markers = Utils.getTileEntitiesWithinAABB(world,
				TELandiaPortalMarker.class,
				new AxisAlignedBB(pos.add(-128, -128, -128), pos.add(128, 128, 128)));
		return markers.stream()
				.min(Comparator.comparingDouble(marker -> marker.getPos().distanceSq(pos)))
				.map(marker -> new ClRes(marker, marker.getBottom().offset(EnumFacing.NORTH)))
				.orElse(null);
	}
	
	static ClRes generateClearance(BlockPos pos, int dimID) {
		final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		WorldServer world = server.worldServerForDimension(dimID);
		ClRes search = searchPortal(world, pos);
		if (search != null) {
			return search;
		}
		BlockPos loc = null;
		EnumFacing facing = null;
		for (BlockPos.MutableBlockPos cand: BlockPos.getAllInBoxMutable(
				new BlockPos(pos.getX()-3, 59, pos.getZ()-3),
				new BlockPos(pos.getX()+3, 256, pos.getZ()+3))) {
			if ((facing = checkValidClearance(world, cand)) != null
					&& (loc == null || cand.getY() > loc.getY())) {
				loc = cand.toImmutable();
				break;
			}
		}
		if (loc == null) {
			loc = new BlockPos(pos.getX(), Math.max(pos.getY(), 77), pos.getZ());
			for (BlockPos.MutableBlockPos setters: BlockPos.getAllInBoxMutable(
					loc.add(-2, 0, -2), loc.add(2, 0, 2))) {
				if (!world.getBlockState(setters).isNormalCube()) {
					world.setBlockState(setters, Blocks.DIRT.getDefaultState());
				}
			}
			for (BlockPos.MutableBlockPos setters: BlockPos.getAllInBoxMutable(
					loc.add(-2, 1, -2), loc.add(2, 3, 2))) {
				world.setBlockState(setters, Blocks.AIR.getDefaultState());
			}
		}
		generatePortal(world, loc);
		if (facing == null) facing = EnumFacing.NORTH;
		return new ClRes(null, loc.offset(facing));
	}
	
	static EnumFacing checkValidClearance(World world, BlockPos pos) {
		if (!world.getBlockState(pos).isNormalCube()) {
			return null;
		}
		for (EnumFacing facing: EnumFacing.HORIZONTALS) {
			if (Tools.canTeleportTo(null, new Coord4D(pos.offset(facing), world))) {
				return facing;
			}
		}
		return null;
	}
	
	static void generatePortal(World world, BlockPos pos) {
		world.setBlockState(pos.add(0,3,0),
				LandCraft.landia_portal_marker.getDefaultState()
				.withProperty(BlockLandiaPortalMarker.ACTIVATED, true));
		for (int i=1; i<=2; ++i) {
			world.setBlockState(pos.add(0,i,0), Blocks.AIR.getDefaultState());
		}
	}
}
