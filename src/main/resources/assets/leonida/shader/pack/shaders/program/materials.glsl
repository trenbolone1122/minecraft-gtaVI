// Leonida Shaders: block.properties ids to material ids (see block.properties).
int materialFromBlockId(int id) {
	if (id == 10001) return MAT_WATER;
	if (id == 10002) return MAT_GLASS;
	if (id == 10003) return MAT_TOWER_GLASS;
	if (id == 10004) return MAT_WINDOW;
	if (id == 10005) return MAT_NEON;
	if (id == 10006) return MAT_ASPHALT;
	if (id == 10007 || id == 10013) return MAT_METAL;
	if (id == 10008 || id == 10012) return MAT_FOLIAGE;
	if (id == 10009) return MAT_LAMP;
	if (id == 10010) return MAT_POLISHED;
	if (id == 10011) return MAT_SAND;
	return MAT_DEFAULT;
}
