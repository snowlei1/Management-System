package cn.edu.jxnu.civicsresources.user;

import java.util.List;

public record UserPage(List<UserView> items, long total, int page, int size) {
}
