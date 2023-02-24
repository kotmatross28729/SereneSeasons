package serenetweaks.handlers;

import java.util.ArrayList;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.Type;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import sereneseasons.api.season.Season.SubSeason;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonASMHelper;
import serenetweaks.data.TimeStampsWorldSavedData;

public class SnowRecalculationHandler {
	
	private static ArrayList<Chunk> recalculationQueue = new ArrayList<Chunk>();
	
	@SubscribeEvent
	public void onTick(TickEvent.WorldTickEvent event) {
		Type type = event.type;
		Side side = event.side;
		Phase phase = event.phase;
		World world = event.world;
		if (world.provider.dimensionId != 0) {
			return;
		}
		if (!(type == Type.WORLD && side == Side.SERVER)) {
			return;
		}
		if (world.isRemote) {
			return;
		}
		if (recalculationQueue.size() == 0) {
			return;
		}
		int i = 0;
		int c = 0;
		while (recalculationQueue.size() > i && c < 20) {
			Chunk chunk = recalculationQueue.get(i);
			if (chunk.isChunkLoaded && chunk.isTerrainPopulated) {
				boolean success = true;
				int posX = chunk.xPosition * 16;
				int posZ = chunk.zPosition * 16;
				for (int k2 = 0; k2 < 16; ++k2) {
		            for (int j3 = 0; j3 < 16; ++j3) {
		                int posY1 = chunk.getPrecipitationHeight(posX + k2, posZ + j3);
		                int posY2 = posY1 - 1;
	
		                if (world.canBlockFreeze(posX, posY2, posZ, false)) {
		                	success = success && world.setBlock(posX, posY2, posZ, Blocks.ice, 0, 2);
		                }
	
		                if (world.canSnowAtBody(posX, posY1, posZ, true)) {
		                	success = success && world.setBlock(posX, posY1, posZ, Blocks.snow_layer, 0, 2);
		                }
		                
		                if (shouldMelt(world, posX, posZ)) {
		                	if (world.getBlock(posX, posY2, posZ) == Blocks.ice) {
		                		success = success && world.setBlock(posX, posY2, posZ, Blocks.water, 0, 2);
		                	}
		                	if (world.getBlock(posX, posY1, posZ) == Blocks.snow_layer) {
		                		success = success && world.setBlock(posX, posY1, posZ, Blocks.air, 0, 2);
		                	}
		                }
		            }
		        }
				if (success) {
					recalculationQueue.remove(i);
					c++;
				} else {
					i++;
				}
			} else {
				i++;
			}
		}
		
	}
	
	@SubscribeEvent
	public void onChunkLoaded(ChunkEvent.Load event) {
		if(!ModConfig.seasons.shouldRecalculateSnow) {
			return;
		}
		World world = event.world;
		if (world.isRemote) {
			return;
		}
		Chunk chunk = event.getChunk();
		if (world.provider.dimensionId != 0) {
			return;
		}
		int currentTime = (int) (System.currentTimeMillis()/1000/60);
		int savedTime = TimeStampsWorldSavedData.getChunkTimeStamp(chunk);
		if (currentTime - savedTime > ModConfig.seasons.timeToRecalculateSnow) {
			recalculationQueue.add(chunk);
		}
	}
	
	@SubscribeEvent
	public void playerJoinedWorld(PlayerLoggedInEvent event) {
		if(!ModConfig.seasons.shouldRecalculateSnow) {
			return;
		}
		EntityPlayer player = event.player;
		World world = player.worldObj;
		if (world.provider.dimensionId != 0) {
			return;
		}
		if (world.isRemote) {
			return;
		}
		for (int i = -5; i < 5; i++) {
			for (int j = -5; j < 5; j++) {
				Chunk chunk = world.getChunkFromChunkCoords(((int)player.posX/16) + i, ((int)player.posZ/16) + j);
				int currentTime = (int) (System.currentTimeMillis()/1000/60);
				int savedTime = TimeStampsWorldSavedData.getChunkTimeStamp(chunk);
				if (currentTime - savedTime > ModConfig.seasons.timeToRecalculateSnow) {
					recalculationQueue.add(chunk);
				}
			}
		}
	}
	
	@SubscribeEvent
	public void onChunkUnLoaded(ChunkEvent.Unload event) {
		World world = event.world;
		if (world.isRemote) {
			return;
		}
		Chunk chunk = event.getChunk();
		if (world.provider.dimensionId != 0) {
			return;
		}
		if(!removeFromRecalculationQueue(chunk)) {
			int currentTime = (int) (System.currentTimeMillis()/1000/60);
			TimeStampsWorldSavedData.setChunkTimeStamp(chunk, currentTime);
		}
	}
	
	@SubscribeEvent
	public void playerLeftWorld(PlayerLoggedOutEvent event) {
		EntityPlayer player = event.player;
		World world = player.worldObj;
		if (world.provider.dimensionId != 0) {
			return;
		}
		if (world.isRemote) {
			return;
		}
		int currentTime = (int) (System.currentTimeMillis()/1000/60);		
		for (int i = -5; i < 5; i++) {
			for (int j = -5; j < 5; j++) {
				Chunk chunk = world.getChunkFromChunkCoords(((int)player.posX/16) + i, ((int)player.posZ/16) + j);
				if(!removeFromRecalculationQueue(chunk)) {
					TimeStampsWorldSavedData.setChunkTimeStamp(chunk, currentTime);
				}
			}
		}
	}
	
	private boolean shouldMelt(World world, int x, int z) {
		BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        SubSeason subSeason = SeasonHelper.getSeasonState(world).getSubSeason();
        float f = SeasonASMHelper.getFloatTemperature(subSeason, biome, x, x, z);
        
		if (f >= 0.15F)
        {
            return true;
        }
		return false;
	}
	
	private boolean removeFromRecalculationQueue(Chunk chunk) {
		for (int i = 0; i < recalculationQueue.size(); i++) {
			Chunk queueChunk = recalculationQueue.get(i);
			if (chunk.xPosition == queueChunk.xPosition && chunk.zPosition == queueChunk.zPosition) {
				recalculationQueue.remove(i);
				i--;
				return true;
			}
		}
		return false;
	};
	
}