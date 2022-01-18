#nullable enable

using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace UbisoftConnectProxy
{
    public class Startup
    {
        public Startup(IConfiguration configuration)
        {
        }

        public void ConfigureServices(IServiceCollection services)
        {
        }

        public void Configure(IApplicationBuilder app, IWebHostEnvironment env)
        {
            var synchronizer = app.ApplicationServices.GetRequiredService<Synchronizer>();
            IProxy proxy = new JavaProxy(synchronizer);
            app.Use(async (context, before) =>
            {
                await before();
                await proxy.ForwardAsync(context);
            });
        }
    }
}
