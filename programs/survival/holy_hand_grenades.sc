__holds(entity, item_regex, enchantment) -> 
(
	if (entity~'gamemode_id'==3, return(0));
	for(l('mainhand','offhand'),
		holds = query(entity, 'holds', _);
		if( holds,
			l(what, count, nbt) = holds;
			if ((what ~ item_regex) && (enchs = get(nbt,'Enchantments[]')),
				if (type(enchs)!='list', enchs = l(enchs));
				for (enchs, 
					if ( get(_,'id') == 'minecraft:'+enchantment,
						lvl = max(lvl, get(_,'lvl'))
					)
				)	
			)
		)
	);
	lvl
);
 
__distance(v1, v2) -> sqrt(reduce(v1-v2,_*_+_a,0));

__on_player_uses_item(player, item_tuple, hand) -> 
(
	if (hand != 'mainhand', return());
	power_level = __holds(player, 'fire_charge', 'power');
	if (power_level == 0, return());
	__deploy_missile(player, power_level)
);

__deploy_missile(player, power) -> 
(
	__create_bullet(player, power);
	if ((player ~ 'gamemode_id')%2, return());
	slot = player ~ 'selected_slot';
	l(item, count, nbt) = inventory_get(player, slot);
	inventory_set(player, slot, count-1)
);

__create_bullet(player, power) ->
(
	look = player ~ 'look';
	fireball_pos = (player ~ 'pos')+l(0, player ~ 'eye_height', 0)+look;
	fireball = spawn('fireball',fireball_pos,
		str('{power:[%.2f,%.2f,%.2f],direction:[0.0,0.0,0.0]},ExplosionPower:0',look/5)
	);
	sound('item.firecharge.use',fireball_pos, 1, 1);
	entity_event(fireball,'on_removed','__explode', look, power)
);

__explode(entity, direction_vec, power) -> (
	l(x,y,z) = pos(entity);
	block = first(diamond(x,y,z,3,3), !__is_invalid_for_smashing(_));
	if (block, __cascade_smash(pos(entity), 1.2*power, direction_vec, pos(block)))
);

__is_invalid_for_smashing(block) -> 
	air(block) || block == 'lava' || block == 'water' || 
	block == 'bedrock' || block == 'barrier' || 
	block ~ 'command_block' || block == 'structure_block' || block == 'jigsaw';

__cascade_smash(center_pos, ttl, bias, position) ->
(
	block = block(position);
	if (ttl <= 0 || __is_invalid_for_smashing(block), return());
	data_component = if (data = block_data(block), 
		',TileEntityData:'+data,
		''
	);
	properties_component = if ( property_list = filter(block_properties(block),_!='waterlogged'),
			',Properties:{'+ join(',', map (property_list,
				value = property(block, _);
				if (block == 'chest' && _ == 'type', value = 'single');
				_+':"'+value+'"'
			))+'}'
		, // else
			''
	);
	block_name = str(block);
	set(position, if(property(block,'waterlogged')=='true','water','air'));
	l(x,y,z) = position;
	l(cx, cy, cz) = center_pos;
	l(dx, dy, dz) = bias;
	falling_block = spawn(
		'falling_block',
		x+0.5, y+0.5, z+0.5, 
		'{BlockState:{Name:"minecraft:'+block_name+'"'+properties_component+'}'+data_component+',Time:1}'
	);
	modify(falling_block,'motion',
		(x-cx)/10+rand(0.05)+dx,
		2*ttl/10+0.5+rand(0.2)+(y-cy)/10+dy,
		(z-cz)/10+rand(0.05)+dz
	);
	if (!rand(6), particle('explosion',position+rand(1)));
	if (!rand(6), sound('entity.dragon_fireball.explode',position, 0.8+rand(0.2), 0.4+rand(0.3)));
	penalty = 0.5+rand(1);
	for (rect(x,y,z),
		if(!__is_invalid_for_smashing(_) && (dist = __distance(pos(_), position)) > 0,
			schedule(
				1*ceil(rand(3*dist*dist)), 
				'__cascade_smash', 
				center_pos, 
				ttl-penalty*dist, 
				bias,
				pos(_) )
		)
	)
)